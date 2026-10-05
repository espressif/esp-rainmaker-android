// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif.ui.webrtc

/**
 * Pure-Kotlin (no org.webrtc types) trickle-ICE ordering helper for
 * [WebRtcViewportManager].
 *
 * Remote ICE candidates trickled by the camera can arrive before the SDP answer
 * has actually been applied via setRemoteDescription(). libwebrtc silently drops
 * addIceCandidate() while remoteDescription is null, so such candidates MUST be
 * queued and flushed only once the answer is applied — otherwise a relay-only
 * peer loses its single TURN candidate and ICE stalls (NO_CONNECTED_CANDIDATE_PAIR).
 *
 * The generic parameter [C] stands in for org.webrtc.IceCandidate so this class
 * stays unit-testable on the plain JVM. The manager wires the sinks to the real
 * PeerConnection objects (peerConnectionFoundMap[key].addIceCandidate / localPeer).
 */
class PendingIceCandidateQueue<C>(
    /** Supplies recipientClientId for peerKey() normalization. */
    private val fallbackClientId: () -> String?,
    /** Add a candidate to the peer registered under [clientId]. */
    private val addToPeer: (clientId: String, candidate: C) -> Unit,
    /** Safety-net sink once remoteDescriptionApplied; returns whether it was added. */
    private val addToLocalPeer: (candidate: C) -> Boolean,
) {
    private val lock = Any()
    private val pending = mutableMapOf<String, MutableList<C>>()
    private val registeredPeers = mutableSetOf<String>()

    /**
     * Threading: offer() runs on the signaling (websocket) thread while
     * onRemoteDescriptionApplied() is invoked from the libwebrtc SDP-observer thread,
     * and reset() from whoever calls stop(). State is guarded by [lock]; the sinks
     * are always invoked OUTSIDE the lock because PeerConnection.addIceCandidate()
     * blocks on libwebrtc's signaling thread, which is the very thread delivering
     * onSetSuccess() -> holding the lock across the sink could deadlock.
     */
    @Volatile
    var remoteDescriptionApplied = false
        private set

    /** Mirrors WebRtcViewportManager.peerKey(): normalize null/empty -> fallback. */
    fun peerKey(senderClientId: String?): String =
        senderClientId?.takeIf { it.isNotEmpty() } ?: fallbackClientId() ?: ""

    /** Mirrors checkAndAddIceCandidate(): add now if possible, else queue. */
    fun offer(senderClientId: String?, candidate: C) {
        val clientId = peerKey(senderClientId)
        val tryLocalPeer: Boolean
        synchronized(lock) {
            when {
                registeredPeers.contains(clientId) -> tryLocalPeer = false
                remoteDescriptionApplied -> tryLocalPeer = true
                else -> {
                    pending.getOrPut(clientId) { mutableListOf() }.add(candidate)
                    return
                }
            }
        }
        if (!tryLocalPeer) {
            addToPeer(clientId, candidate)
            return
        }
        // Safety net: peer registered under a differing key. Fall back to the
        // local peer; if it is gone, keep queueing rather than dropping.
        if (!addToLocalPeer(candidate)) {
            synchronized(lock) { pending.getOrPut(clientId) { mutableListOf() }.add(candidate) }
        }
    }

    /** Mirrors setRemoteDescription onSetSuccess(): register, flip flag, flush. */
    fun onRemoteDescriptionApplied(senderClientId: String?) {
        val clientId = peerKey(senderClientId)
        synchronized(lock) {
            remoteDescriptionApplied = true
            registeredPeers.add(clientId)
        }
        flush(clientId)
    }

    /** Mirrors handlePendingIceCandidates(): drain this key + any stray keys. */
    fun flush(clientId: String) {
        val drained = mutableListOf<C>()
        synchronized(lock) {
            if (!registeredPeers.contains(clientId)) return
            pending.remove(clientId)?.let { drained.addAll(it) }
            pending.values.forEach { drained.addAll(it) }
            pending.clear()
        }
        drained.forEach { addToPeer(clientId, it) }
    }

    /** Reset all state for session teardown (performStopCleanup). */
    fun reset() {
        synchronized(lock) {
            remoteDescriptionApplied = false
            registeredPeers.clear()
            pending.clear()
        }
    }

    fun pendingCount(): Int = synchronized(lock) { pending.values.sumOf { it.size } }
}
