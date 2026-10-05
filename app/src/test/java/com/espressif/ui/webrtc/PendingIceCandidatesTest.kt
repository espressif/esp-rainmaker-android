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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the trickle-ICE ordering bug fixed in this MR.
 *
 * Bug: remote ICE candidates trickled by the camera were passed to
 * PeerConnection.addIceCandidate() BEFORE setRemoteDescription() was applied,
 * so libwebrtc silently dropped the camera relay candidate -> ICE stalled at
 * NO_CONNECTED_CANDIDATE_PAIR.
 *
 * These tests exercise the pure-Kotlin PendingIceCandidateQueue, which carries
 * the same ordering/normalization rules as WebRtcViewportManager. No org.webrtc
 * types, so it runs under ./gradlew :app:testDebugUnitTest (JUnit4).
 */
class PendingIceCandidatesTest {

    // Records where each candidate ended up, preserving order.
    private data class Added(val target: String, val candidate: String)

    private val added = mutableListOf<Added>()
    private var recipientClientId: String? = "viewer-1"

    private fun newQueue() = PendingIceCandidateQueue<String>(
        fallbackClientId = { recipientClientId },
        addToPeer = { clientId, c -> added.add(Added("peer:$clientId", c)) },
        addToLocalPeer = { c -> added.add(Added("localPeer", c)) },
    )

    /** THE REGRESSION: a candidate arriving before the answer must be queued, not dropped. */
    @Test
    fun candidateBeforeAnswer_isQueued_notAdded() {
        val q = newQueue()
        q.offer(senderClientId = null, candidate = "relay-cand")
        assertTrue("candidate must not reach the peer before the answer", added.isEmpty())
        assertEquals(1, q.pendingCount())
    }

    /** ...and is flushed to the peer, in order, once the answer is applied. */
    @Test
    fun queuedCandidates_flushedInOrder_afterAnswerApplied() {
        val q = newQueue()
        q.offer(null, "cand-a")
        q.offer(null, "cand-b")
        assertTrue(added.isEmpty())

        q.onRemoteDescriptionApplied(senderClientId = null) // camera MASTER answers with null id

        assertEquals(0, q.pendingCount())
        assertEquals(
            listOf(
                Added("peer:viewer-1", "cand-a"),
                Added("peer:viewer-1", "cand-b"),
            ),
            added,
        )
    }

    /** peerKey() normalizes a null/empty senderClientId to recipientClientId. */
    @Test
    fun peerKey_normalizesNullAndEmpty_toRecipient() {
        val q = newQueue()
        assertEquals("viewer-1", q.peerKey(null))
        assertEquals("viewer-1", q.peerKey(""))
        assertEquals("cam-9", q.peerKey("cam-9"))
    }

    /** A candidate queued under a null id is still flushed when the answer registers
     *  the peer under the normalized key (single-master collapse). */
    @Test
    fun candidateQueuedUnderNullId_flushedWhenPeerRegisteredUnderNormalizedKey() {
        val q = newQueue()
        q.offer(null, "relay-cand")            // queued under "viewer-1"
        q.onRemoteDescriptionApplied("viewer-1")
        assertEquals(listOf(Added("peer:viewer-1", "relay-cand")), added)
        assertEquals(0, q.pendingCount())
    }

    /** After the answer, a matching-key candidate goes straight to the peer. */
    @Test
    fun candidateAfterAnswer_matchingKey_goesToPeer() {
        val q = newQueue()
        q.onRemoteDescriptionApplied(null) // registers under "viewer-1"
        q.offer(null, "late-cand")
        assertEquals(listOf(Added("peer:viewer-1", "late-cand")), added)
        assertEquals(0, q.pendingCount())
    }

    /** Safety net: after the answer, an unknown-key candidate falls back to localPeer
     *  rather than being re-queued forever. */
    @Test
    fun candidateAfterAnswer_unknownKey_fallsBackToLocalPeer() {
        val q = newQueue()
        q.onRemoteDescriptionApplied(null) // registers only "viewer-1"
        q.offer(senderClientId = "stranger", candidate = "x-cand")
        assertEquals(listOf(Added("localPeer", "x-cand")), added)
        assertEquals(0, q.pendingCount())
    }

    /** flush() also drains candidates queued under a differing id (one master/session). */
    @Test
    fun flush_drainsStrayQueuesToo() {
        val q = newQueue()
        q.offer("cam-9", "cand-under-cam")   // queued under "cam-9"
        recipientClientId = "cam-9"          // answer will normalize to same peer
        q.onRemoteDescriptionApplied("cam-9")
        assertEquals(listOf(Added("peer:cam-9", "cand-under-cam")), added)
        assertEquals(0, q.pendingCount())
    }
}
