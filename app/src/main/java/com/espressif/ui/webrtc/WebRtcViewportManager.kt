package com.espressif.ui.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import com.espressif.webrtc.*
import com.espressif.ui.webrtc.WebRtcChannelInfo
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.RendererCommon
import org.webrtc.VideoCapturer
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.RTCStats
import java.net.URI
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.Date

/**
 * Holds a snapshot of WebRTC video stats for UI display.
 */
data class WebRtcStats(
    val currentFps: Float = 0f,
    val receivedFps: Float = 0f,
    val droppedFps: Float = 0f,
    val totalFramesDropped: Long = 0,
    val totalBytesReceived: Long = 0,
    val totalPacketsReceived: Long = 0,
    val totalPacketsLost: Long = 0,
    val jitterMs: Double = 0.0,
    val videoCodec: String = "N/A",
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val currentBitrateKbps: Long = 0,
    val streamDurationMs: Long = 0
)

class WebRtcViewportManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner? = null
) {
    private val TAG = "WebRtcViewportManager"

    private var rootEglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    @Volatile private var localPeer: PeerConnection? = null
    private var remoteView: SurfaceViewRenderer? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    // Local media sending
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSender: RtpSender? = null

    // Compose-observable state so portrait and landscape UIs share one source of truth.
    // Reading these from a @Composable auto-subscribes for recomposition; writes from any
    // thread go through the snapshot system and notify observers.
    var isVideoSendEnabled by mutableStateOf(false)
        private set
    var isAudioSendEnabled by mutableStateOf(false)
        private set

    private var onMediaToggleChanged: ((videoEnabled: Boolean, audioEnabled: Boolean) -> Unit)? = null

    private var client: SignalingServiceWebSocketClient? = null
    private var audioManager: AudioManager? = null
    private val peerIceServers = mutableListOf<IceServer>()
    private val peerConnectionFoundMap = mutableMapOf<String, PeerConnection>()
    private val pendingIceCandidatesMap = mutableMapOf<String, MutableList<IceCandidate>>()

    private var mChannelArn: String? = null
    private var mStreamArn: String? = null
    private var mWssEndpoint: String? = null
    private var webrtcEndpoint: String? = null
    private var mClientId: String? = null
    private var mRegion: String? = null
    private var master = false
    private var recipientClientId: String? = null

    private var isStreamActive = false
    @Volatile private var pendingRenegotiation = false
    private var streamStartTime = 0L

    private var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    companion object {
        private const val ENABLE_INTEL_VP8_ENCODER = true
        private const val ENABLE_H264_HIGH_PROFILE = true
        private const val ENABLE_AUDIO = false

        @Volatile
        private var peerConnectionFactoryInitialized = false

        private const val LOCAL_MEDIA_STREAM_LABEL = "KvsLocalMediaStream"
        private const val VIDEO_TRACK_ID = "KvsVideoTrack"
        private const val AUDIO_TRACK_ID = "KvsAudioTrack"
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_FPS = 20
    }

    fun setCallbacks(
        onConnectionStateChanged: ((Boolean) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        this.onConnectionStateChanged = onConnectionStateChanged
        this.onError = onError
    }

    fun setOnMediaToggleChanged(listener: ((videoEnabled: Boolean, audioEnabled: Boolean) -> Unit)?) {
        onMediaToggleChanged = listener
    }

    // --- Local media sending (video/audio toggle while peer connection is active) ---

    /**
     * Enables sending video from the phone camera to the remote peer.
     * Creates the capture pipeline if needed and adds the track to the peer connection.
     * If the connection is already established, triggers SDP renegotiation.
     */
    fun enableVideoSending() {
        try {
            Log.i(TAG, "Enabling video sending...")
            val factory = peerConnectionFactory ?: run {
                Log.e(TAG, "Cannot enable video: PeerConnectionFactory is null")
                return
            }
            val eglBase = rootEglBase ?: run {
                Log.e(TAG, "Cannot enable video: EglBase is null")
                return
            }

            // Create video capturer if needed
            if (videoCapturer == null) {
                videoCapturer = createVideoCapturer() ?: run {
                    Log.e(TAG, "Failed to create video capturer")
                    return
                }
            }

            // Create video source and track if needed
            if (videoSource == null) {
                videoSource = factory.createVideoSource(false)
                val surfaceTextureHelper = SurfaceTextureHelper.create(
                    Thread.currentThread().name, eglBase.eglBaseContext
                )
                videoCapturer?.initialize(
                    surfaceTextureHelper,
                    context.applicationContext,
                    videoSource!!.capturerObserver
                )
            }

            if (localVideoTrack == null) {
                localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            }

            // Start or restart video capture
            try {
                videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                Log.i(TAG, "Video capture started: ${VIDEO_WIDTH}x${VIDEO_HEIGHT}@${VIDEO_FPS}fps")
            } catch (e: Exception) {
                Log.w(TAG, "Video capturer may already be started: ${e.message}")
            }

            // Enable (unmute) the track
            localVideoTrack?.setEnabled(true)

            // Add video track to peer connection on first enable only
            val peer = localPeer
            if (peer != null && localVideoTrack != null && localVideoSender == null) {
                localVideoSender = peer.addTrack(localVideoTrack, listOf(LOCAL_MEDIA_STREAM_LABEL))
                Log.i(TAG, "Video track added to peer connection (first enable)")

                // Renegotiate if connection is already established
                if (isStreamActive && client?.isOpen == true) {
                    Log.i(TAG, "Renegotiating to include video track...")
                    renegotiate()
                } else {
                    pendingRenegotiation = true
                    Log.i(TAG, "Connection not ready; deferring renegotiation until connected")
                }
            } else if (localVideoSender != null) {
                // Track already in peer connection - just re-enabled above
                Log.i(TAG, "Video track re-enabled in peer connection")
            }

            isVideoSendEnabled = true
            onMediaToggleChanged?.invoke(isVideoSendEnabled, isAudioSendEnabled)
            Log.i(TAG, "Video sending enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling video sending: ${e.message}", e)
        }
    }

    /**
     * Disables video sending. Stops the camera capture and mutes the track.
     * The track stays in the peer connection so it can be re-enabled without renegotiation.
     */
    fun disableVideoSending() {
        try {
            Log.i(TAG, "Disabling video sending...")

            // Stop video capture to save battery / resources
            try {
                videoCapturer?.stopCapture()
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error stopping video capture: ${e.message}")
            }

            // Disable (mute) the track - keep it in the peer connection
            localVideoTrack?.setEnabled(false)

            isVideoSendEnabled = false
            onMediaToggleChanged?.invoke(isVideoSendEnabled, isAudioSendEnabled)
            Log.i(TAG, "Video sending disabled (track muted, still in peer connection)")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling video sending: ${e.message}", e)
        }
    }

    /**
     * Toggles video sending on/off.
     * @return the new state of video sending
     */
    fun toggleVideoSending(): Boolean {
        if (isVideoSendEnabled) {
            disableVideoSending()
        } else {
            enableVideoSending()
        }
        return isVideoSendEnabled
    }

    /**
     * Enables sending audio (microphone) to the remote peer.
     * Creates the audio pipeline if needed and adds the track to the peer connection.
     */
    fun enableAudioSending() {
        try {
            Log.i(TAG, "Enabling audio sending...")
            val factory = peerConnectionFactory ?: run {
                Log.e(TAG, "Cannot enable audio: PeerConnectionFactory is null")
                return
            }

            // Create audio source and track if needed
            if (audioSource == null) {
                audioSource = factory.createAudioSource(MediaConstraints())
            }
            if (localAudioTrack == null) {
                localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
            }

            // Enable (unmute) the track
            localAudioTrack?.setEnabled(true)

            // Add audio track to peer connection on first enable only
            val peer = localPeer
            if (peer != null && localAudioTrack != null && localAudioSender == null) {
                localAudioSender = peer.addTrack(localAudioTrack, listOf(LOCAL_MEDIA_STREAM_LABEL))
                Log.i(TAG, "Audio track added to peer connection (first enable)")

                // Renegotiate if connection is already established
                if (isStreamActive && client?.isOpen == true) {
                    Log.i(TAG, "Renegotiating to include audio track...")
                    renegotiate()
                } else {
                    pendingRenegotiation = true
                    Log.i(TAG, "Connection not ready; deferring renegotiation until connected")
                }
            } else if (localAudioSender != null) {
                // Track already in peer connection - just re-enabled above
                Log.i(TAG, "Audio track re-enabled in peer connection")
            }

            isAudioSendEnabled = true
            onMediaToggleChanged?.invoke(isVideoSendEnabled, isAudioSendEnabled)
            Log.i(TAG, "Audio sending enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling audio sending: ${e.message}", e)
        }
    }

    /**
     * Disables audio sending. Mutes the track but keeps it in the peer connection
     * so it can be re-enabled without renegotiation.
     */
    fun disableAudioSending() {
        try {
            Log.i(TAG, "Disabling audio sending...")

            // Disable (mute) the track - keep it in the peer connection
            localAudioTrack?.setEnabled(false)

            isAudioSendEnabled = false
            onMediaToggleChanged?.invoke(isVideoSendEnabled, isAudioSendEnabled)
            Log.i(TAG, "Audio sending disabled (track muted, still in peer connection)")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling audio sending: ${e.message}", e)
        }
    }

    /**
     * Toggles audio sending on/off.
     * @return the new state of audio sending
     */
    fun toggleAudioSending(): Boolean {
        if (isAudioSendEnabled) {
            disableAudioSending()
        } else {
            enableAudioSending()
        }
        return isAudioSendEnabled
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera1Enumerator(false)
        // Try front-facing camera first
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    Log.d(TAG, "Front-facing camera capturer created")
                    return capturer
                }
            }
        }
        // Fall back to back-facing camera
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    Log.d(TAG, "Back-facing camera capturer created")
                    return capturer
                }
            }
        }
        Log.e(TAG, "No camera available")
        return null
    }

    /**
     * Renegotiates the SDP to include newly added media tracks.
     * This is needed when a video or audio track is added after the peer connection
     * has already been established.
     */
    private fun renegotiate() {
        val peer = localPeer ?: run {
            Log.e(TAG, "Cannot renegotiate: localPeer is null")
            return
        }
        if (client?.isOpen != true) {
            Log.e(TAG, "Cannot renegotiate: signaling client is not open")
            return
        }

        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peer.createOffer(object : KinesisVideoSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                Log.i(TAG, "Renegotiation offer created successfully")
                peer.setLocalDescription(KinesisVideoSdpObserver(), sessionDescription)

                if (client?.isOpen == true) {
                    val message = Message.createOfferMessage(sessionDescription, mClientId ?: "")
                    client?.sendSdpOffer(message)
                    Log.i(TAG, "Renegotiation offer sent to peer")
                }
            }

            override fun onCreateFailure(error: String) {
                super.onCreateFailure(error)
                Log.e(TAG, "Renegotiation offer creation failed: $error")
            }
        }, constraints)
    }

    fun start(
        channelArn: String,
        streamArn: String?,
        wssEndpoint: String,
        webrtcEndpoint: String?,
        region: String,
        iceServers: List<IceServer>,
        surfaceViewRenderer: SurfaceViewRenderer,
        isMaster: Boolean = false,
        clientId: String? = null
    ) {
        if (rootEglBase != null) {
            Log.w(TAG, "WebRTC already started")
            return
        }

        this.mChannelArn = channelArn
        this.mStreamArn = streamArn
        this.mWssEndpoint = wssEndpoint
        this.webrtcEndpoint = webrtcEndpoint
        this.mRegion = region
        this.master = isMaster
        this.remoteView = surfaceViewRenderer

        // Use provided client ID or generate new one
        mClientId = clientId ?: UUID.randomUUID().toString()
        if (clientId != null) {
            Log.d(TAG, "Reusing client ID: $clientId")
        }

        // Initialize audio manager
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        rootEglBase = EglBase.create()

        // Add STUN server
        val stun = IceServer.builder(
            String.format("stun:stun.kinesisvideo.%s.amazonaws.com:443", region)
        ).createIceServer()
        peerIceServers.add(stun)

        // Add TURN servers
        peerIceServers.addAll(iceServers)

        // Initialize PeerConnectionFactory once per process
        if (!peerConnectionFactoryInitialized) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions()
            )
            peerConnectionFactoryInitialized = true
        }

        val vdf = DefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext)
        val vef = DefaultVideoEncoderFactory(
            rootEglBase!!.eglBaseContext,
            ENABLE_INTEL_VP8_ENCODER,
            ENABLE_H264_HIGH_PROFILE
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(vdf)
            .setVideoEncoderFactory(vef)
            .setAudioDeviceModule(
                JavaAudioDeviceModule.builder(context.applicationContext)
                    .createAudioDeviceModule()
            )
            .createPeerConnectionFactory()

        // Enable WebRTC debug logs
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)

        // Initialize SurfaceViewRenderer
        remoteView?.init(rootEglBase!!.eglBaseContext, null)
        // Set scaling to fit (scale instead of crop)
        remoteView?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        // Enable mirroring if needed (set to false for camera view)
        remoteView?.setMirror(false)

        // Create peer connection
        createLocalPeerConnection()

        // Initialize WebSocket connection
        initWsConnection()
    }

    private fun createLocalPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(peerIceServers)

        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED

        // Network stability improvements
        rtcConfig.iceConnectionReceivingTimeout = 10000
        rtcConfig.iceBackupCandidatePairPingInterval = 5000
        rtcConfig.iceCandidatePoolSize = 4
        rtcConfig.enableDscp = true
        rtcConfig.suspendBelowMinBitrate = false

        localPeer = peerConnectionFactory?.createPeerConnection(rtcConfig, object : KinesisVideoPeerConnection() {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                super.onIceCandidate(iceCandidate)
                val message = createIceCandidateMessage(iceCandidate)
                Log.d(TAG, "Sending IceCandidate to remote peer $iceCandidate")
                client?.sendIceCandidate(message)
            }

            override fun onAddStream(mediaStream: MediaStream) {
                super.onAddStream(mediaStream)
                Log.d(TAG, "Adding remote video stream to the view")
                addRemoteStreamToVideoView(mediaStream)
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                super.onIceConnectionChange(iceConnectionState)

                // Check if we're already stopping - don't process callbacks
                if (isStopping) {
                    Log.d(TAG, "Ignoring ICE connection change - already stopping")
                    return
                }

                when (iceConnectionState) {
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.w(TAG, "ICE connection failed - terminal state, stopping session")
                        isStreamActive = false
                        // Terminal (no recovery without renegotiation) — tear down so the UI
                        // leaves the "playing" screen. Run on a fresh thread, not inline: this
                        // callback is on the libwebrtc signaling thread, and disposing the peer
                        // here lets a queued event lock a freed mutex → SIGABRT.
                        if (!isStopping) {
                            Thread({ stop() }, "ice-failed-stop").start()
                        }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "ICE connection disconnected - connection may recover, not tearing down")
                        // Do NOT call stop() - ICE DISCONNECTED is often transient on mobile networks
                        // Just notify the UI so it can show a status indicator if desired
                        if (!isStopping) {
                            try {
                                onConnectionStateChanged?.invoke(false)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error invoking connection state callback: ${e.message}", e)
                            }
                        }
                    }
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        if (!isStreamActive && !isStopping) {
                            streamStartTime = System.currentTimeMillis()
                            isStreamActive = true
                            Log.i(TAG, "Stream started - duration tracking began")
                            startStatsCollection()
                        }
                        if (pendingRenegotiation) {
                            pendingRenegotiation = false
                            Log.i(TAG, "Flushing deferred renegotiation after CONNECTED")
                            renegotiate()
                        }
                        if (!isStopping) {
                            try {
                                onConnectionStateChanged?.invoke(true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error invoking connection state callback: ${e.message}", e)
                            }
                        }
                    }
                    else -> {}
                }
            }
        })
    }

    private fun initWsConnection() {
        val masterEndpoint = "$mWssEndpoint?${Constants.CHANNEL_ARN_QUERY_PARAM}=$mChannelArn"
        val viewerEndpoint = "$mWssEndpoint?${Constants.CHANNEL_ARN_QUERY_PARAM}=$mChannelArn&${Constants.CLIENT_ID_QUERY_PARAM}=$mClientId"

        val credentials = WebRtcConstants.getCredentialsProvider().credentials
        val endpoint = if (master) masterEndpoint else viewerEndpoint
        val signedUri = getSignedUri(endpoint, credentials, mRegion ?: "")

        if (signedUri == null) {
            onError?.invoke("Failed to get signed URI")
            return
        }

        val wsHost = signedUri.toString()

        val signalingListener = object : SignalingListener() {
            override fun onSdpOffer(offerEvent: Event) {
                Log.d(TAG, "Received SDP Offer: Setting Remote Description")

                val sdp = Event.parseOfferEvent(offerEvent)
                localPeer?.setRemoteDescription(
                    object : KinesisVideoSdpObserver() {},
                    SessionDescription(SessionDescription.Type.OFFER, sdp)
                )

                this@WebRtcViewportManager.recipientClientId = offerEvent.senderClientId
                Log.d(TAG, "Received SDP offer for client ID: ${this@WebRtcViewportManager.recipientClientId}. Creating answer")
                createSdpAnswer()
            }

            override fun onSdpAnswer(answerEvent: Event) {
                Log.d(TAG, "SDP answer received from signaling")

                // Snapshot localPeer once. stop() may null it on the main thread between
                // any two reads on this signaling-worker thread.
                val peer = localPeer
                if (peer == null) {
                    Log.w(TAG, "onSdpAnswer: localPeer is null (session stopping); ignoring answer")
                    return
                }

                val sdp = Event.parseSdpEvent(answerEvent)
                val sdpAnswer = SessionDescription(SessionDescription.Type.ANSWER, sdp)

                peer.setRemoteDescription(
                    object : KinesisVideoSdpObserver() {
                        override fun onCreateFailure(error: String) {
                            super.onCreateFailure(error)
                            Log.e(TAG, "Failed to set remote description: $error")
                        }
                    },
                    sdpAnswer
                )

                Log.d(TAG, "Answer Client ID: ${answerEvent.senderClientId}")
                // AWS Kinesis Video Signaling can deliver an SDP answer with a null
                // senderClientId (observed when the master is the camera/MASTER role and
                // doesn't fill it). Skip the per-client bookkeeping — the remote
                // description is already applied above, so the session can still
                // progress through ICE.
                val senderClientId = answerEvent.senderClientId
                if (senderClientId != null) {
                    peerConnectionFoundMap[senderClientId] = peer
                    handlePendingIceCandidates(senderClientId)
                } else {
                    Log.w(TAG, "onSdpAnswer: senderClientId is null; skipping peer-map registration")
                }
            }

            override fun onIceCandidate(message: Event) {
                Log.d(TAG, "Received ICE candidate from remote")
                val iceCandidate = Event.parseIceCandidate(message)
                if (iceCandidate != null) {
                    checkAndAddIceCandidate(message, iceCandidate)
                } else {
                    Log.e(TAG, "Invalid ICE candidate: $message")
                }
            }

            override fun onError(errorMessage: Event) {
                Log.e(TAG, "Received error message: $errorMessage")
                onError?.invoke("WebRTC error: $errorMessage")
            }

            override fun onException(e: Exception) {
                Log.e(TAG, "Signaling client returned exception: ${e.message}")
                onError?.invoke("WebRTC exception: ${e.message}")
            }
        }

        try {
            client = SignalingServiceWebSocketClient(wsHost, signalingListener, Executors.newFixedThreadPool(10))
            Log.d(TAG, "Client connection ${if (client?.isOpen == true) "Successful" else "Failed"}")

            if (client?.isOpen == true) {
                Log.d(TAG, "Client connected to Signaling service")
                // Both master and viewer create SDP offers
                // Master creates offer to start the session
                // Viewer creates offer to request video stream
                if (localPeer != null) {
                    Log.d(TAG, if (master) "Creating SDP offer as master" else "Creating SDP offer as viewer")
                    createSdpOffer()
                } else {
                    Log.e(TAG, "Peer connection is null, cannot create SDP offer")
                    onError?.invoke("Peer connection not initialized")
                }
            } else {
                onError?.invoke("Failed to connect to signaling service")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception with websocket client: $e")
            onError?.invoke("WebSocket exception: ${e.message}")
        }
    }

    private fun createSdpOffer() {
        if (localPeer == null) {
            Log.e(TAG, "Cannot create SDP offer: peer connection is null")
            onError?.invoke("Peer connection not initialized")
            return
        }

        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        if (ENABLE_AUDIO) {
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        localPeer?.createOffer(object : KinesisVideoSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer?.setLocalDescription(object : KinesisVideoSdpObserver() {}, sessionDescription)
                val message = Message.createOfferMessage(sessionDescription, mClientId ?: "")

                if (client?.isOpen == true) {
                    client?.sendSdpOffer(message)
                    Log.d(TAG, "SDP Offer created and sent")
                } else {
                    Log.e(TAG, "Cannot send SDP offer: WebSocket connection is not open")
                    onError?.invoke("WebSocket connection lost")
                }
            }

            override fun onCreateFailure(error: String) {
                super.onCreateFailure(error)
                Log.e(TAG, "Failed to create SDP offer: $error")
                onError?.invoke("Failed to create SDP offer: $error")
            }
        }, constraints)
    }

    private fun createSdpAnswer() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        localPeer?.createAnswer(object : KinesisVideoSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer?.setLocalDescription(object : KinesisVideoSdpObserver() {}, sessionDescription)
                val message = Message.createAnswerMessage(sessionDescription, master, recipientClientId ?: mClientId ?: "")
                client?.sendSdpAnswer(message)
                Log.d(TAG, "SDP Answer created and sent")
            }

            override fun onCreateFailure(error: String) {
                super.onCreateFailure(error)
                Log.e(TAG, "Failed to create SDP answer: $error")
                onError?.invoke("Failed to create SDP answer: $error")
            }
        }, constraints)
    }

    private fun addRemoteStreamToVideoView(stream: MediaStream) {
        val videoTrack = stream.videoTracks?.firstOrNull()
        val audioTrack = stream.audioTracks?.firstOrNull()

        // Store references to tracks
        this.remoteVideoTrack = videoTrack
        this.remoteAudioTrack = audioTrack

        if (ENABLE_AUDIO && audioTrack != null) {
            audioTrack.setEnabled(true)
            Log.d(TAG, "remoteAudioTrack received: State=${audioTrack.state().name}")
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true
        }

        if (videoTrack != null) {
            val view = remoteView
            if (view == null) {
                Log.e(TAG, "Cannot add video sink: remoteView is null")
                onError?.invoke("Video view not initialized")
                return
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    // Double-check view is still valid
                    val currentView = remoteView
                    if (currentView == null) {
                        Log.e(TAG, "remoteView became null before adding sink")
                        return@post
                    }

                    Log.d(TAG, "remoteVideoTrackId=${videoTrack.id()} videoTrackState=${videoTrack.state()}")
                    videoTrack.addSink(currentView)
                    onConnectionStateChanged?.invoke(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in setting remote video view: $e", e)
                    onError?.invoke("Error setting video view: ${e.message}")
                }
            }
        } else {
            Log.e(TAG, "Error in setting remote track: no video track in stream")
            onError?.invoke("No video track in stream")
        }
    }

    private fun checkAndAddIceCandidate(message: Event, iceCandidate: IceCandidate) {
        val peer = peerConnectionFoundMap[message.senderClientId]
        if (peer == null) {
            val pendingCandidates = pendingIceCandidatesMap.getOrPut(message.senderClientId) { mutableListOf() }
            pendingCandidates.add(iceCandidate)
            Log.d(TAG, "Peer connection not found, adding ICE candidate to pending list")
        } else {
            val addIce = peer.addIceCandidate(iceCandidate)
            Log.d(TAG, "Added ice candidate $iceCandidate ${if (addIce) "Successfully" else "Failed"}")
        }
    }

    private fun handlePendingIceCandidates(clientId: String) {
        val pendingCandidates = pendingIceCandidatesMap.remove(clientId)
        if (pendingCandidates != null) {
            val peer = peerConnectionFoundMap[clientId]
            if (peer != null) {
                for (candidate in pendingCandidates) {
                    peer.addIceCandidate(candidate)
                }
                Log.d(TAG, "Added ${pendingCandidates.size} pending ICE candidates")
            }
        }
    }

    private fun createIceCandidateMessage(iceCandidate: IceCandidate): Message {
        val sdpMid = iceCandidate.sdpMid
        val sdpMLineIndex = iceCandidate.sdpMLineIndex
        val sdp = iceCandidate.sdp

        val messagePayload = "{\"candidate\":\"$sdp\",\"sdpMid\":\"$sdpMid\",\"sdpMLineIndex\":$sdpMLineIndex}"

        val senderClientId = if (master) "" else (mClientId ?: "")

        val encodedBytes = android.util.Base64.encode(
            messagePayload.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val encodedPayload = String(encodedBytes)

        return Message("ICE_CANDIDATE", recipientClientId ?: "", senderClientId, encodedPayload)
    }

    private fun getSignedUri(endpoint: String, credentials: com.amazonaws.auth.AWSCredentials, region: String): URI? {
        val accessKey = credentials.awsAccessKeyId
        val secretKey = credentials.awsSecretKey
        val sessionToken = if (credentials is com.amazonaws.auth.AWSSessionCredentials) {
            credentials.sessionToken
        } else ""

        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            Log.e(TAG, "Failed to fetch credentials!")
            return null
        }

        return try {
            val endpointUri = URI.create(endpoint)
            val wssUri = URI.create("wss://${endpointUri.host}")
            AwsV4Signer.sign(endpointUri, accessKey, secretKey, sessionToken ?: "", wssUri, region, Date().time)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign URI: $e")
            null
        }
    }

    @Volatile
    private var isStopping = false

    fun stop() {
        // Prevent multiple simultaneous stop calls
        if (isStopping) {
            Log.w(TAG, "Stop already in progress, ignoring duplicate call")
            return
        }

        synchronized(this) {
            if (isStopping) {
                return
            }
            isStopping = true
        }

        Log.d(TAG, "Stopping WebRTC connection")

        // Stop stats collection before anything else
        stopStatsCollection()
        onStatsUpdated = null
        onMediaToggleChanged = null

        isStreamActive = false
        // Clear the deferred-renegotiation flag so a reused instance does not fire a
        // stale renegotiation on the next session's first CONNECTED.
        pendingRenegotiation = false

        // Store callback references and clear them immediately to prevent callbacks during cleanup
        val connectionCallback = onConnectionStateChanged
        val errorCallback = onError
        onConnectionStateChanged = null
        onError = null

        // Clean up local video sending resources
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping video capturer: ${e.message}")
        }
        videoCapturer?.dispose()
        videoCapturer = null
        localVideoTrack?.setEnabled(false)
        localVideoTrack = null
        localVideoSender = null
        videoSource?.dispose()
        videoSource = null
        isVideoSendEnabled = false

        // Clean up local audio sending resources
        localAudioTrack?.setEnabled(false)
        localAudioTrack = null
        localAudioSender = null
        audioSource?.dispose()
        audioSource = null
        isAudioSendEnabled = false

        // Store references before clearing
        val videoTrack = remoteVideoTrack
        val audioTrack = remoteAudioTrack
        val view = remoteView

        // Clear references immediately to prevent new operations
        remoteVideoTrack = null
        remoteAudioTrack = null

        // Remove video track sink before disposing (must be on main thread)
        videoTrack?.let { track ->
            if (view != null) {
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    // Already on main thread, execute directly
                    try {
                        track.removeSink(view)
                        Log.d(TAG, "Removed video track sink")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing video track sink: $e", e)
                    }
                } else {
                    // Post to main thread and wait for completion
                    val latch = java.util.concurrent.CountDownLatch(1)
                    handler.post {
                        try {
                            if (remoteView == view) { // Double-check view hasn't changed
                                track.removeSink(view)
                                Log.d(TAG, "Removed video track sink")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error removing video track sink: $e", e)
                        } finally {
                            latch.countDown()
                        }
                    }
                    // Wait up to 1 second for sink removal
                    try {
                        latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Interrupted while waiting for sink removal", e)
                    }
                }
            }
        }

        // Disable and clear audio track
        try {
            audioTrack?.setEnabled(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling audio track: $e", e)
        }

        // Disconnect WebSocket
        try {
            client?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WebSocket: $e", e)
        }
        client = null

        // Dispose PeerConnection (this will also dispose tracks)
        // Clear reference first to prevent callbacks from accessing disposed connection
        val peerToDispose = localPeer
        localPeer = null
        try {
            peerToDispose?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing PeerConnection: $e", e)
        }

        remoteView = null

        try {
            rootEglBase?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing EglBase: $e", e)
        }
        rootEglBase = null

        // Do not dispose PeerConnectionFactory; re-create on next start only
        peerConnectionFactory = null

        // Reset audio manager
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio manager: $e", e)
        }
        audioManager = null

        // Shutdown stats executor
        statsExecutor.shutdownNow()

        // Clear maps
        peerConnectionFoundMap.clear()
        pendingIceCandidatesMap.clear()
        peerIceServers.clear()

        // Invoke callback on main thread if available (callbacks already cleared above, use stored reference)
        try {
            connectionCallback?.let { callback ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        callback(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error invoking connection state callback: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting connection state callback: ${e.message}", e)
        }

        isStopping = false
        Log.d(TAG, "WebRTC connection stopped")
    }

    fun isActive(): Boolean {
        return rootEglBase != null && client?.isOpen == true
    }

    /**
     * Get current channel information if available
     */
    fun getChannelInfo(): WebRtcChannelInfo? {
        return if (mChannelArn != null && mWssEndpoint != null && mRegion != null) {
            WebRtcChannelInfo(
                channelArn = mChannelArn!!,
                streamArn = mStreamArn,
                wssEndpoint = mWssEndpoint!!,
                webrtcEndpoint = webrtcEndpoint,
                iceServers = peerIceServers.toList(),
                region = mRegion!!
            )
        } else {
            null
        }
    }

    /**
     * Get current client ID
     */
    fun getClientId(): String? {
        return mClientId
    }

    /**
     * Get the EglBase instance used by this manager
     * This allows sharing the same EglBase context for video transfer
     */
    fun getEglBase(): EglBase? {
        return rootEglBase
    }

    /**
     * Get the PeerConnection instance
     * This allows the fullscreen activity to access the peer for stats collection
     */
    fun getPeerConnection(): PeerConnection? {
        return localPeer
    }

    /**
     * Get the remote video track
     */
    fun getRemoteVideoTrack(): VideoTrack? {
        return remoteVideoTrack
    }

    // --- Stats collection ---
    private val statsExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var statsTask: ScheduledFuture<*>? = null
    @Volatile private var shouldCollectStats = false

    // Delta tracking for stats
    private var lastStatsTime = 0L
    private var lastFramesDropped = 0L
    private var lastBytesReceived = 0L

    // Current stats snapshot
    @Volatile var currentStats: WebRtcStats = WebRtcStats()
        private set

    private var onStatsUpdated: ((WebRtcStats) -> Unit)? = null

    fun setOnStatsUpdated(listener: ((WebRtcStats) -> Unit)?) {
        onStatsUpdated = listener
    }

    fun startStatsCollection() {
        val peer = localPeer ?: return
        stopStatsCollection()
        shouldCollectStats = true
        lastStatsTime = 0
        lastFramesDropped = 0
        lastBytesReceived = 0
        statsTask = statsExecutor.scheduleWithFixedDelay({
            if (!shouldCollectStats || localPeer == null) {
                stopStatsCollection()
                return@scheduleWithFixedDelay
            }
            val p = localPeer ?: return@scheduleWithFixedDelay
            try {
                p.getStats { report ->
                    if (!shouldCollectStats) return@getStats
                    var fps = 0f; var width = 0; var height = 0
                    var framesDropped = 0L; var bytesRx = 0L; var packetsRx = 0L
                    var packetsLost = 0L; var jitter = 0.0; var codec = "N/A"

                    for (entry in report.statsMap) {
                        val stat = entry.value
                        if (stat.type == "inbound-rtp") {
                            val m = stat.members
                            fps = (m["framesPerSecond"]?.toString()?.toFloatOrNull()) ?: 0f
                            width = (m["frameWidth"]?.toString()?.toIntOrNull()) ?: 0
                            height = (m["frameHeight"]?.toString()?.toIntOrNull()) ?: 0
                            framesDropped = (m["framesDropped"]?.toString()?.toLongOrNull()) ?: 0
                            bytesRx = (m["bytesReceived"]?.toString()?.toLongOrNull()) ?: 0
                            packetsRx = (m["packetsReceived"]?.toString()?.toLongOrNull()) ?: 0
                            packetsLost = (m["packetsLost"]?.toString()?.toLongOrNull()) ?: 0
                            jitter = ((m["jitter"]?.toString()?.toDoubleOrNull()) ?: 0.0) * 1000
                        }
                        if (stat.type == "codec") {
                            val mimeType = stat.members["mimeType"]?.toString()
                            if (mimeType != null) codec = mimeType
                        }
                    }

                    val now = System.currentTimeMillis()
                    var droppedFps = 0f
                    var bitrateKbps = 0L
                    if (lastStatsTime > 0 && now > lastStatsTime) {
                        val deltaSec = (now - lastStatsTime) / 1000f
                        val deltaDropped = framesDropped - lastFramesDropped
                        if (deltaSec > 0 && deltaDropped >= 0) droppedFps = deltaDropped / deltaSec
                        val deltaBytes = bytesRx - lastBytesReceived
                        if (deltaSec > 0 && deltaBytes >= 0) bitrateKbps = ((deltaBytes * 8) / deltaSec / 1000).toLong()
                    }
                    lastFramesDropped = framesDropped
                    lastBytesReceived = bytesRx
                    lastStatsTime = now

                    val duration = if (isStreamActive && streamStartTime > 0) now - streamStartTime else 0L

                    currentStats = WebRtcStats(
                        currentFps = fps,
                        receivedFps = fps + droppedFps,
                        droppedFps = droppedFps,
                        totalFramesDropped = framesDropped,
                        totalBytesReceived = bytesRx,
                        totalPacketsReceived = packetsRx,
                        totalPacketsLost = packetsLost,
                        jitterMs = jitter,
                        videoCodec = codec,
                        frameWidth = width,
                        frameHeight = height,
                        currentBitrateKbps = bitrateKbps,
                        streamDurationMs = duration
                    )
                    onStatsUpdated?.invoke(currentStats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting stats: ${e.message}", e)
                stopStatsCollection()
            }
        }, 0, 1, TimeUnit.SECONDS)
        Log.d(TAG, "Stats collection started")
    }

    fun stopStatsCollection() {
        shouldCollectStats = false
        statsTask?.cancel(true)
        statsTask = null
    }

    /**
     * Set the original viewport renderer (for transferring video back)
     */
    fun setViewportRenderer(renderer: SurfaceViewRenderer) {
        originalViewportRenderer = renderer
        Log.d(TAG, "Stored viewport renderer for later transfer back")
    }

    /**
     * Get the original viewport renderer (for transferring video back)
     */
    fun getViewportRenderer(): SurfaceViewRenderer? {
        return originalViewportRenderer
    }

    private var originalViewportRenderer: SurfaceViewRenderer? = null

    /**
     * Transfer video rendering to a new SurfaceViewRenderer
     * This allows moving the video from viewport to fullscreen without reconnecting
     */
    fun transferVideoTo(newRenderer: SurfaceViewRenderer) {
        try {
            if (newRenderer == null) {
                Log.e(TAG, "Cannot transfer: newRenderer is null")
                onError?.invoke("Renderer is null")
                return
            }

            val videoTrack = remoteVideoTrack
            if (videoTrack == null) {
                Log.w(TAG, "No video track to transfer")
                onError?.invoke("No video track available")
                return
            }

            if (rootEglBase == null) {
                Log.e(TAG, "Cannot transfer video: EglBase is null")
                onError?.invoke("EglBase is null")
                return
            }

            // Use originalViewportRenderer if available (set before transfer), otherwise use current remoteView
            val oldView = originalViewportRenderer ?: remoteView

            Log.d(TAG, "Transfer: oldView=${oldView != null} (originalViewportRenderer=${originalViewportRenderer != null}, remoteView=${remoteView != null}), newRenderer=${newRenderer != null}")

            // Ensure we're on main thread
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                transferVideoTrackInternal(videoTrack, oldView, newRenderer)
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        transferVideoTrackInternal(videoTrack, oldView, newRenderer)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in transferVideoTrackInternal on main thread: ${e.message}", e)
                        onError?.invoke("Transfer failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in transferVideoTo: ${e.message}", e)
            e.printStackTrace()
            onError?.invoke("Transfer failed: ${e.message}")
        }
    }

    private fun transferVideoTrackInternal(
        videoTrack: VideoTrack,
        oldView: SurfaceViewRenderer?,
        newRenderer: SurfaceViewRenderer
    ) {
        try {
            if (newRenderer == null) {
                Log.e(TAG, "newRenderer is null in transferVideoTrackInternal")
                throw IllegalStateException("newRenderer is null")
            }

            Log.d(TAG, "Orientation change: Moving video from viewport to landscape renderer")
            Log.d(TAG, "Video track: id=${videoTrack.id()}, state=${videoTrack.state()}, enabled=${videoTrack.enabled()}")

            // originalViewportRenderer should already be set by setViewportRenderer() before transfer
            // But ensure it's set if somehow it wasn't
            if (originalViewportRenderer == null && oldView != null) {
                originalViewportRenderer = oldView
                Log.d(TAG, "Stored viewport renderer reference for transfer back (fallback)")
            }

            // Remove video track from old renderer (viewport) - MUST happen first
            oldView?.let { view ->
                try {
                    Log.d(TAG, "Removing video sink from viewport renderer (oldView=${view.hashCode()})...")
                    videoTrack.removeSink(view)
                    Log.d(TAG, "Video sink removed from viewport - video should stop in viewport")
                } catch (e: Exception) {
                    Log.w(TAG, "Note removing sink from viewport: ${e.message} - may already be removed")
                    // Don't throw - continue with transfer
                }
            }

            // Also remove from current remoteView if it's different from oldView
            remoteView?.let { currentView ->
                if (currentView != oldView && currentView != newRenderer) {
                    try {
                        Log.d(TAG, "Removing video sink from current remoteView (currentView=${currentView.hashCode()})...")
                        videoTrack.removeSink(currentView)
                        Log.d(TAG, "Video sink removed from current remoteView")
                    } catch (e: Exception) {
                        Log.w(TAG, "Note removing sink from current remoteView: ${e.message}")
                        // Don't throw - continue with transfer
                    }
                }
            }

            // Immediately add to new renderer (landscape) - same video stream, different display
            try {
                Log.d(TAG, "Adding video sink to landscape renderer (newRenderer=${newRenderer.hashCode()})...")

                // Verify rootEglBase is available
                if (rootEglBase == null) {
                    Log.e(TAG, "Root EglBase is null - cannot transfer video")
                    throw IllegalStateException("Root EglBase is null")
                }

                // Don't add sink if it's already the current renderer
                if (remoteView == newRenderer) {
                    Log.w(TAG, "New renderer is already the current remoteView - skipping transfer")
                    return
                }

                // Configure renderer for landscape display
                try {
                    newRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    newRenderer.setMirror(false)
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting renderer properties: ${e.message} - continuing anyway")
                }

                // Add video track - this is the same stream, just displayed in landscape
                // Note: Both renderers must share the same EglBase context (verified in WebRtcActivity.onCreate)
                videoTrack.addSink(newRenderer)
                remoteView = newRenderer
                Log.d(TAG, "Video now displaying in landscape - same stream, different orientation")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding video to landscape renderer: ${e.message}", e)
                e.printStackTrace()
                onError?.invoke("Error transferring video: ${e.message}")
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during orientation change: ${e.message}", e)
            e.printStackTrace()
            onError?.invoke("Error transferring video: ${e.message}")
            throw e
        }
    }
}
