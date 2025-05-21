package com.espressif.webrtc;

import android.util.Log;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

/**
 * Listener for Peer connection events. Prints event info to the logs at debug level.
 */
public class KinesisVideoPeerConnection implements PeerConnection.Observer {

    private final static String TAG = "KVSPeerConnection";

    public KinesisVideoPeerConnection() {

    }

    /**
     * Triggered when the SignalingState changes.
     */
    @Override
    public void onSignalingChange(final PeerConnection.SignalingState signalingState) {

        Log.d(TAG, "onSignalingChange(): signalingState = [" + signalingState + "]");

    }

    /**
     * Triggered when the IceConnectionState changes.
     */
    @Override
    public void onIceConnectionChange(final PeerConnection.IceConnectionState iceConnectionState) {

        Log.d(TAG, "onIceConnectionChange(): iceConnectionState = [" + iceConnectionState + "]");

    }

    /**
     * Triggered when the ICE connection receiving status changes.
     */
    @Override
    public void onIceConnectionReceivingChange(final boolean connectionChange) {

        Log.d(TAG, "onIceConnectionReceivingChange(): connectionChange = [" + connectionChange + "]");

    }

    /**
     * Triggered when the IceGatheringState changes.
     */
    @Override
    public void onIceGatheringChange(final PeerConnection.IceGatheringState iceGatheringState) {

        Log.d(TAG, "onIceGatheringChange(): iceGatheringState = [" + iceGatheringState + "]");

    }

    /**
     * Triggered when a new ICE candidate has been found.
     */
    @Override
    public void onIceCandidate(final IceCandidate iceCandidate) {

        Log.d(TAG, "onIceCandidate(): iceCandidate = [" + iceCandidate + "]");

    }

    /**
     * Triggered when some ICE candidates have been removed.
     */
    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] iceCandidates) {

        Log.d(TAG, "onIceCandidatesRemoved(): iceCandidates Length = [" + iceCandidates.length + "]");

    }

    /**
     * Triggered when the ICE candidate pair is changed.
     */
    @Override
    public void onSelectedCandidatePairChanged(final CandidatePairChangeEvent event) {

        final String eventString = "{" +
                String.join(", ",
                        "reason: " + event.reason,
                        "remote: " + event.remote,
                        "local: " + event.local,
                        "lastReceivedMs: " + event.lastDataReceivedMs) +
                "}";
        Log.d(TAG, "onSelectedCandidatePairChanged(): event = " + eventString);

    }

    /**
     * Triggered when media is received on a new stream from remote peer.
     */
    @Override
    public void onAddStream(final MediaStream mediaStream) {

        Log.d(TAG, "onAddStream(): mediaStream = [" + mediaStream + "]");

    }

    /**
     * Triggered when a remote peer close a stream.
     */
    @Override
    public void onRemoveStream(final MediaStream mediaStream) {

        Log.d(TAG, "onRemoveStream(): mediaStream = [" + mediaStream + "]");

    }

    /**
     * Triggered when a remote peer opens a DataChannel.
     */
    @Override
    public void onDataChannel(final DataChannel dataChannel) {

        Log.d(TAG, "onDataChannel(): dataChannel = [" + dataChannel + "]");

    }

    /**
     * Triggered when renegotiation is necessary.
     */
    @Override
    public void onRenegotiationNeeded() {

        Log.d(TAG, "onRenegotiationNeeded():");

    }

    /**
     * Triggered when a new track is signaled by the remote peer, as a result of setRemoteDescription.
     */
    @Override
    public void onAddTrack(final RtpReceiver rtpReceiver, final MediaStream[] mediaStreams) {

        Log.d(TAG, "onAddTrack(): rtpReceiver = [" + rtpReceiver + "], " +
                "mediaStreams Length = [" + mediaStreams.length + "]");

    }
}
