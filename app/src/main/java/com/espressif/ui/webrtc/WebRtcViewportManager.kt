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
import com.amazonaws.services.kinesisvideo.model.ChannelRole
import com.espressif.local_control.EspLocalDevice
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
import org.webrtc.BaselineDefaultVideoEncoderFactory
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.LowLatencyDefaultVideoDecoderFactory
import org.webrtc.RTCStats
import java.net.URI
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.Date
import android.os.Handler
import android.os.Looper
import org.webrtc.EglRenderer.FrameListener

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

/**
 * STATE CONTRACT:
 *   - state == RUNNING|STARTING means "actively trying to play" — UI should treat as 'playing'
 *   - state == STOPPING|STOPPED inside a retry/fallback chain is INTERNAL — UI must not react
 *   - onTerminallyStopped() is the ONLY signal the UI should use to flip isPlaying back to false
 *
 * Callers driving a fallback (e.g. local→KVS) MUST call markRestartInFlight() on the
 * outgoing manager before invoking stop() so the manager does not fire onTerminallyStopped
 * during the intermediate cleanup. The flag is cleared automatically when start() /
 * startLocal() transitions back to RUNNING.
 *
 * Explicit user-initiated stops (a Stop button tap, etc.) MUST call userStop() instead of
 * stop() so onTerminallyStopped fires regardless of any restart-in-flight flag.
 */
class WebRtcViewportManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner? = null
) {
    private val TAG = "WebRtcViewportManager"

    private var rootEglBase: EglBase? = null
    @Volatile private var isStarting = false
    private var peerConnectionFactory: PeerConnectionFactory? = null
    // Held so stop() can call release() on the Java-side ADM. PeerConnectionFactory.dispose()
    // only drops its reference — without this, native audio capture/playback threads keep
    // running and the mic stays hot after stop().
    private var audioDeviceModule: org.webrtc.audio.AudioDeviceModule? = null
    @Volatile private var localPeer: PeerConnection? = null
    private var remoteView: SurfaceViewRenderer? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    // Cached params from the most recent start() / startLocal() call. Used by
    // restartIfStopped() to replay the original entry point after the session has
    // been torn down by the process-level ON_STOP observer.
    private sealed class CachedStartParams {
        data class Kvs(
            val channelArn: String,
            val streamArn: String?,
            val wssEndpoint: String,
            val webrtcEndpoint: String?,
            val region: String,
            val iceServers: List<IceServer>,
            val dataEndpoint: String?,
            val surfaceViewRenderer: SurfaceViewRenderer,
            val isMaster: Boolean,
            val clientId: String?
        ) : CachedStartParams()

        data class Local(
            val localDevice: EspLocalDevice,
            val surfaceViewRenderer: SurfaceViewRenderer,
            val clientId: String?
        ) : CachedStartParams()
    }
    private var lastStartParams: CachedStartParams? = null

    // Tracks every SurfaceViewRenderer currently attached to this session. When the set
    // goes empty the session has no UI consumer, so we schedule a delayed stop — a short
    // gap (e.g. orientation transfer) does not tear the session down, but a long idle
    // window does.
    private val attachedRenderers = mutableSetOf<SurfaceViewRenderer>()
    private val attachLock = Any()
    private val idleStopHandler = Handler(Looper.getMainLooper())
    private val idleStopRunnable = Runnable {
        val empty = synchronized(attachLock) { attachedRenderers.isEmpty() }
        if (empty) {
            Log.d(TAG, "Idle stop firing: no renderers attached for ${IDLE_STOP_DELAY_MS}ms")
            stop()
        }
    }

    // --- Surface watchdog ---
    // Detects the case where frames keep arriving from the decoder but no renderer is
    // actually drawing them (surface dead, e.g. Compose tab switch with parent kept
    // alive). Compares received-frame delta vs rendered-frame delta every 3s while
    // state == RUNNING; if received > 0 && rendered == 0, force-detach every renderer
    // so the existing idle-stop path tears the session down.
    private val surfaceWatchdogHandler = Handler(Looper.getMainLooper())
    private val receivedFrameCounter = AtomicLong(0L)
    private val renderedFrameCounterByRenderer =
        ConcurrentHashMap<SurfaceViewRenderer, AtomicLong>()
    private val frameListenerByRenderer =
        ConcurrentHashMap<SurfaceViewRenderer, FrameListener>()
    private val lastWatchdogReceivedTotal = AtomicLong(0L)
    private val lastWatchdogRenderedTotal = AtomicLong(0L)
    // Consecutive "frames in, nothing rendered" ticks since last reset. We require
    // rendering to have worked at least once before counting — avoids false-positive
    // tear-downs during decoder/GPU warm-up.
    private val watchdogBadTicks = java.util.concurrent.atomic.AtomicInteger(0)

    // Separate, count-only sink registered alongside the renderer sink on the remote
    // video track. Lets us measure decode-side throughput independently of any single
    // renderer's surface state.
    private val countingReceiveSink = org.webrtc.VideoSink {
        receivedFrameCounter.incrementAndGet()
    }

    private val surfaceWatchdogRunnable = object : Runnable {
        override fun run() {
            try {
                if (state == ManagerState.RUNNING) {
                    val curReceived = receivedFrameCounter.get()
                    val curRendered = renderedFrameCounterByRenderer.values.sumOf { it.get() }
                    val deltaRcv = curReceived - lastWatchdogReceivedTotal.get()
                    val deltaRdr = curRendered - lastWatchdogRenderedTotal.get()
                    lastWatchdogReceivedTotal.set(curReceived)
                    lastWatchdogRenderedTotal.set(curRendered)
                    // Only count this tick as bad if rendering has worked before
                    // (totalRendered > 0) — avoids false positives during decoder
                    // and GPU surface warm-up. Require N consecutive bad ticks
                    // before concluding the surface is dead.
                    if (deltaRcv > 0 && deltaRdr == 0L && curRendered > 0L) {
                        val bad = watchdogBadTicks.incrementAndGet()
                        Log.d(TAG, "Surface watchdog: received=$deltaRcv rendered=0 (bad tick $bad/$WATCHDOG_BAD_TICKS_THRESHOLD, totalRendered=$curRendered)")
                        if (bad >= WATCHDOG_BAD_TICKS_THRESHOLD) {
                            Log.w(TAG, "Surface watchdog: bad-ticks threshold reached — force-detaching all renderers")
                            val snap = synchronized(attachLock) { attachedRenderers.toList() }
                            snap.forEach { r ->
                                try { detachRenderer(r) } catch (_: Exception) {}
                            }
                            watchdogBadTicks.set(0)
                        }
                    } else if (synchronized(attachLock) { attachedRenderers.isEmpty() } && curReceived > 0) {
                        // v5: RUNNING with frames flowing but NO renderer ever attached
                        // (or all detached due to a Compose transient flap during STARTING).
                        // The original "totalRendered > 0" predicate never triggers here
                        // because there's no FrameListener to bump the rendered counter.
                        val bad = watchdogBadTicks.incrementAndGet()
                        Log.d(TAG, "Surface watchdog: state=RUNNING but no renderers attached (received total=$curReceived, bad tick $bad/$WATCHDOG_BAD_TICKS_THRESHOLD)")
                        if (bad >= WATCHDOG_BAD_TICKS_THRESHOLD) {
                            Log.w(TAG, "Surface watchdog: RUNNING with no UI consumer for $WATCHDOG_BAD_TICKS_THRESHOLD ticks — force-stopping session")
                            watchdogBadTicks.set(0)
                            // No renderer to detach; go straight to stop().
                            stop()
                        }
                    } else if (deltaRcv > 0 && deltaRdr == 0L && curRendered == 0L && curReceived > WARM_UP_RECEIVED_THRESHOLD) {
                        // v6: renderer IS attached but its surface never came up since
                        // session start (e.g. ProcessLifecycleOwner foreground-restart with
                        // a renderer whose surface was destroyed in the background). Frames
                        // arrive in bulk, FrameListener never fires once. Wait until enough
                        // frames have been received to be sure the session is genuinely
                        // active before counting bad ticks.
                        val bad = watchdogBadTicks.incrementAndGet()
                        Log.d(TAG, "Surface watchdog: rendered=0 despite curReceived=$curReceived — surface dead since startup (bad tick $bad/$WATCHDOG_BAD_TICKS_THRESHOLD)")
                        if (bad >= WATCHDOG_BAD_TICKS_THRESHOLD) {
                            Log.w(TAG, "Surface watchdog: rendered=0 after $curReceived frames — force-detaching all renderers")
                            watchdogBadTicks.set(0)
                            val snap = synchronized(attachLock) { attachedRenderers.toList() }
                            snap.forEach { try { detachRenderer(it) } catch (_: Exception) {} }
                            // Once detached, the v5 branch (RUNNING + no renderers) takes
                            // over and/or the state-gated idle stop kicks in.
                        }
                    } else {
                        // Healthy tick, or still warming up (totalRendered == 0 with
                        // received frames below the warm-up threshold).
                        watchdogBadTicks.set(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Watchdog tick error", e)
            }
            // Re-schedule unless we've reached a terminal state
            if (state == ManagerState.RUNNING || state == ManagerState.STARTING) {
                surfaceWatchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private fun startSurfaceWatchdog() {
        surfaceWatchdogHandler.removeCallbacks(surfaceWatchdogRunnable)
        // Reset baselines so the first tick measures only post-RUNNING activity.
        lastWatchdogReceivedTotal.set(receivedFrameCounter.get())
        lastWatchdogRenderedTotal.set(
            renderedFrameCounterByRenderer.values.sumOf { it.get() }
        )
        watchdogBadTicks.set(0)
        surfaceWatchdogHandler.postDelayed(surfaceWatchdogRunnable, WATCHDOG_INTERVAL_MS)
        Log.d(TAG, "Surface watchdog started (interval=${WATCHDOG_INTERVAL_MS}ms)")
    }

    private fun stopSurfaceWatchdog() {
        surfaceWatchdogHandler.removeCallbacks(surfaceWatchdogRunnable)
    }

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
    // Compose-observable so portrait and landscape share the same source of truth
    // and toggling from either side propagates immediately.
    var isIncomingAudioMuted by mutableStateOf(WebRtcConstants.INCOMING_AUDIO_MUTED_BY_DEFAULT)
        private set

    private var onMediaToggleChanged: ((videoEnabled: Boolean, audioEnabled: Boolean) -> Unit)? = null
    private var onIncomingAudioMuteChanged: ((muted: Boolean) -> Unit)? = null

    private var client: SignalingServiceWebSocketClient? = null
    private var localClient: LocalSignalingClient? = null
    private var audioManager: AudioManager? = null
    private val peerIceServers = mutableListOf<IceServer>()
    private val peerConnectionFoundMap = mutableMapOf<String, PeerConnection>()

    // Trickle-ICE ordering lives here now: queue remote candidates until the SDP
    // answer is applied, then flush. Sinks route to the real PeerConnection objects
    // held in peerConnectionFoundMap / localPeer. See PendingIceCandidateQueue.
    private val pendingIceCandidateQueue = PendingIceCandidateQueue<IceCandidate>(
        fallbackClientId = { recipientClientId },
        addToPeer = { clientId, candidate -> peerConnectionFoundMap[clientId]?.addIceCandidate(candidate) },
        addToLocalPeer = { candidate -> localPeer?.addIceCandidate(candidate) ?: false },
    )

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

    enum class ManagerState { IDLE, STARTING, RUNNING, STOPPING, STOPPED }

    @Volatile
    private var state: ManagerState = ManagerState.IDLE
    @Volatile
    private var deferredStopRequested: Boolean = false
    private val stateLock = Any()

    private fun canStart(): Boolean = state == ManagerState.IDLE || state == ManagerState.STOPPED
    private fun canStop(): Boolean = state != ManagerState.IDLE
    private fun canRestart(): Boolean = state == ManagerState.STOPPED

    private var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    /**
     * Fired exactly once per session lifetime when this manager's state transitions to
     * STOPPED *and* no restart is in flight (or when userStop() was used). UI should
     * subscribe and flip its 'playing' flag to false here — and ONLY here. See the
     * STATE CONTRACT kdoc on the class for details.
     */
    var onTerminallyStopped: (() -> Unit)? = null

    @Volatile private var userInitiatedStop = false
    @Volatile private var restartInFlight = false

    // SDP offer retry state
    @Volatile private var sdpAnswerReceived = false
    // The remote-description-applied flag and the trickled-candidate queue both live
    // in pendingIceCandidateQueue now (see its kdoc): libwebrtc drops addIceCandidate()
    // while remoteDescription is null, so candidates queue until onSetSuccess flushes.
    private var offerAttempt = 0
    private val offerRetryHandler = Handler(Looper.getMainLooper())

    // Single-thread executor for media toggle operations (camera open/close is slow; off main thread).
    // Recreated by start()/startLocal() if previously drained by stop().
    private var mediaOpsExecutor: java.util.concurrent.ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, MEDIA_OPS_THREAD_NAME) }

    companion object {
        private const val ENABLE_INTEL_VP8_ENCODER = true
        private const val ENABLE_H264_HIGH_PROFILE = false

        @Volatile
        private var peerConnectionFactoryInitialized = false
        private val pcfInitLock = Any()

        private const val LOCAL_MEDIA_STREAM_LABEL = "KvsLocalMediaStream"
        private const val VIDEO_TRACK_ID = "KvsVideoTrack"
        private const val AUDIO_TRACK_ID = "KvsAudioTrack"
        private const val VIDEO_WIDTH = 240
        private const val VIDEO_HEIGHT = 240
        private const val VIDEO_FPS = 15
        // BWE can not probe its way out of "bandwidth limited" state on its own,
        // so seed it. Without these, libwebrtc default starts at 96 kbps and never
        // climbs because probing is suppressed once limited.
        // 200 kbps is the empirical sweet spot for 240x240 @ 15 fps upstream:
        // - prevents BWE from collapsing to the libwebrtc 96k default (the original
        //   reason this knob exists)
        // - low enough that upstream doesn't steal bandwidth from the FHD downstream
        //   on a shared WiFi link
        // Higher (400/600k) gave the encoder more headroom on motion-blocky frames
        // but cut the RX path from 17-22 fps to 9-14 fps. Not worth the trade.
        private const val VIDEO_MIN_BITRATE_BPS = 200_000
        private const val VIDEO_START_BITRATE_BPS = 600_000
        private const val VIDEO_MAX_BITRATE_BPS = 1_500_000

        private const val MAX_OFFER_RETRIES = 3
        private const val OFFER_TIMEOUT_MS = 5000L

        // Lowered from 30s — keeping mic/audio capture alive for half a minute with
        // no UI consumer is too long. 5s still covers brief orientation/transfer gaps.
        private const val IDLE_STOP_DELAY_MS = 5_000L

        private const val WATCHDOG_INTERVAL_MS = 3_000L
        private const val WATCHDOG_BAD_TICKS_THRESHOLD = 7
        private const val WARM_UP_RECEIVED_THRESHOLD = 30L

        private const val MEDIA_OPS_THREAD_NAME = "WebRtcMediaOps"
        private const val STATS_THREAD_NAME = "WebRtcStats"

        /**
         * Pre-initialize the PeerConnectionFactory on a background thread.
         * Call from Application.onCreate() to avoid blocking the UI thread
         * when the first WebRTC session starts.
         */
        @JvmStatic
        fun preInitializePeerConnectionFactory(context: Context) {
            if (peerConnectionFactoryInitialized) return
            Thread { ensureFactoryInitialized(context) }.start()
        }

        /** Thread-safe, idempotent PeerConnectionFactory.initialize() — a non-atomic
         *  check-then-set let the startup pre-warm thread and start()'s thread both
         *  call initialize() on a cold-start race, which throws on the second call. */
        fun ensureFactoryInitialized(context: Context) {
            if (peerConnectionFactoryInitialized) return
            synchronized(pcfInitLock) {
                if (!peerConnectionFactoryInitialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                            .createInitializationOptions()
                    )
                    peerConnectionFactoryInitialized = true
                    Log.d("WebRtcViewportManager", "PeerConnectionFactory initialized")
                }
            }
        }
    }

    fun setCallbacks(
        onConnectionStateChanged: ((Boolean) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        this.onConnectionStateChanged = onConnectionStateChanged
        this.onError = onError
    }

    /**
     * Schedule a retry if no SDP answer arrives within [OFFER_TIMEOUT_MS].
     * Called after each offer is sent. Cancels any previously pending retry first.
     */
    private fun scheduleOfferRetry() {
        offerRetryHandler.removeCallbacksAndMessages(null)
        offerAttempt++
        Log.d(TAG, "Offer sent (attempt $offerAttempt/$MAX_OFFER_RETRIES), waiting ${OFFER_TIMEOUT_MS}ms for answer")

        val isLastAttempt = offerAttempt >= MAX_OFFER_RETRIES
        val timeout = if (isLastAttempt) OFFER_TIMEOUT_MS * 3 / 2 else OFFER_TIMEOUT_MS

        offerRetryHandler.postDelayed({
            if (sdpAnswerReceived) return@postDelayed

            if (!isLastAttempt) {
                Log.w(TAG, "No SDP answer after ${timeout}ms (attempt $offerAttempt/$MAX_OFFER_RETRIES), resending offer")
                Toast.makeText(context, "No response from camera, retrying... ($offerAttempt/$MAX_OFFER_RETRIES)", Toast.LENGTH_SHORT).show()
                createSdpOffer()
            } else {
                Log.e(TAG, "No SDP answer after $MAX_OFFER_RETRIES attempts (${timeout}ms final wait), giving up")
                onError?.invoke("Camera not responding. Please try again.")
                stop()
            }
        }, timeout)
    }

    fun setOnMediaToggleChanged(listener: ((videoEnabled: Boolean, audioEnabled: Boolean) -> Unit)?) {
        onMediaToggleChanged = listener
    }

    fun setOnIncomingAudioMuteChanged(listener: ((muted: Boolean) -> Unit)?) {
        onIncomingAudioMuteChanged = listener
    }

    /**
     * Toggles mute/unmute of incoming (remote) audio playback.
     * No renegotiation needed — just enables/disables the remote audio track.
     */
    fun toggleIncomingAudio(): Boolean {
        isIncomingAudioMuted = !isIncomingAudioMuted
        remoteAudioTrack?.setEnabled(!isIncomingAudioMuted)
        if (isIncomingAudioMuted) {
            Log.i(TAG, "Incoming audio muted")
        } else {
            Log.i(TAG, "Incoming audio unmuted")
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = true
        }
        onIncomingAudioMuteChanged?.invoke(isIncomingAudioMuted)
        return isIncomingAudioMuted
    }

    // --- Local media sending (video/audio toggle while peer connection is active) ---

    /**
     * Enables sending video from the phone camera to the remote peer.
     * Creates the capture pipeline if needed and adds the track to the peer connection.
     * If the connection is already established, triggers SDP renegotiation.
     */
    fun enableVideoSending() {
        mediaOpsExecutor.execute { enableVideoSendingInternal() }
    }

    private fun enableVideoSendingInternal() {
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
                applyVideoSenderBitrate(localVideoSender)
                applyPeerBitrateConfig(peer)

                // Renegotiate if connection is already established (KVS or local signaling)
                if (isStreamActive && (client?.isOpen == true || localClient != null)) {
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
        mediaOpsExecutor.execute { disableVideoSendingInternal() }
    }

    private fun disableVideoSendingInternal() {
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
        mediaOpsExecutor.execute { enableAudioSendingInternal() }
    }

    private fun enableAudioSendingInternal() {
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

                // Renegotiate if connection is already established (KVS or local signaling).
                // Mirrors the video-send path so the local-signaling audio toggle does not
                // silently defer-and-stall when client?.isOpen is false in local mode.
                if (isStreamActive && (client?.isOpen == true || localClient != null)) {
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
        mediaOpsExecutor.execute { disableAudioSendingInternal() }
    }

    private fun disableAudioSendingInternal() {
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

    /**
     * Adds a local audio track to the peer connection before the SDP offer,
     * so the offer includes audio as sendrecv instead of recvonly.
     */
    private fun addLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return
        val peer = localPeer ?: return

        if (audioSource == null) {
            audioSource = factory.createAudioSource(MediaConstraints())
        }
        if (localAudioTrack == null) {
            localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        }
        localAudioTrack?.setEnabled(true)
        localAudioSender = peer.addTrack(localAudioTrack, listOf(LOCAL_MEDIA_STREAM_LABEL))
        isAudioSendEnabled = true
        Log.i(TAG, "Local audio track added to peer connection (default send)")
    }

    /**
     * Override the BWE start point on the PeerConnection so the encoder is not
     * forced to climb out of the libwebrtc default 96 kbps cold-start.
     */
    private fun applyPeerBitrateConfig(peer: PeerConnection) {
        try {
            val ok = peer.setBitrate(
                VIDEO_MIN_BITRATE_BPS,
                VIDEO_START_BITRATE_BPS,
                VIDEO_MAX_BITRATE_BPS
            )
            Log.i(TAG, "Peer setBitrate min=${VIDEO_MIN_BITRATE_BPS} start=${VIDEO_START_BITRATE_BPS} max=${VIDEO_MAX_BITRATE_BPS} ok=$ok")
        } catch (t: Throwable) {
            Log.w(TAG, "applyPeerBitrateConfig failed: ${t.message}")
        }
    }

    /**
     * Cap the per-encoding maxBitrateBps on the video sender so the bitrate
     * allocator has a real ceiling to aim for instead of the simulcast default.
     */
    private fun applyVideoSenderBitrate(sender: org.webrtc.RtpSender?) {
        if (sender == null) return
        try {
            val params = sender.parameters ?: return
            val encs = params.encodings ?: return
            if (encs.isEmpty()) {
                Log.w(TAG, "applyVideoSenderBitrate: no encodings on video sender")
                return
            }
            for (e in encs) {
                e.minBitrateBps = VIDEO_MIN_BITRATE_BPS
                e.maxBitrateBps = VIDEO_MAX_BITRATE_BPS
                e.maxFramerate = VIDEO_FPS
            }
            sender.parameters = params
            Log.i(TAG, "Video sender encoding capped min=${VIDEO_MIN_BITRATE_BPS} max=${VIDEO_MAX_BITRATE_BPS} maxFps=$VIDEO_FPS")
        } catch (t: Throwable) {
            Log.w(TAG, "applyVideoSenderBitrate failed: ${t.message}")
        }
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
        if (client?.isOpen != true && localClient == null) {
            Log.e(TAG, "Cannot renegotiate: no signaling transport available")
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

                if (localClient != null) {
                    localClient?.sendSdpOffer(sessionDescription.description)
                    Log.i(TAG, "Renegotiation offer sent via local signaling")
                } else if (client?.isOpen == true) {
                    val message = Message.createOfferMessage(sessionDescription, mClientId ?: "")
                    client?.sendSdpOffer(message)
                    Log.i(TAG, "Renegotiation offer sent via KVS")
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
        iceServers: List<IceServer> = emptyList(),
        dataEndpoint: String? = null,
        surfaceViewRenderer: SurfaceViewRenderer,
        isMaster: Boolean = false,
        clientId: String? = null
    ) {
        synchronized(stateLock) {
            if (!canStart()) {
                Log.w(TAG, "start() called in state $state; ignoring")
                return
            }
            state = ManagerState.STARTING
            deferredStopRequested = false
            Log.d(TAG, "state: -> STARTING (start)")
        }
        if (rootEglBase != null) {
            Log.w(TAG, "WebRTC already started")
            return
        }
        // Latch synchronously on the calling thread: rootEglBase is only assigned
        // later inside the background Thread, so without this a second start() would
        // pass the guard and double-init (EglBase/factory/renderer.init twice).
        isStarting = true

        ensureExecutorsAlive()

        this.mChannelArn = channelArn
        this.mStreamArn = streamArn
        this.mWssEndpoint = wssEndpoint
        this.webrtcEndpoint = webrtcEndpoint
        this.mRegion = region
        this.master = isMaster
        this.remoteView = surfaceViewRenderer
        lastStartParams = CachedStartParams.Kvs(
            channelArn, streamArn, wssEndpoint, webrtcEndpoint, region,
            iceServers, dataEndpoint, surfaceViewRenderer, isMaster, clientId
        )
        attachRenderer(surfaceViewRenderer)

        // Use provided client ID or generate new one
        mClientId = clientId ?: UUID.randomUUID().toString()
        if (clientId != null) {
            Log.d(TAG, "Reusing client ID: $clientId")
        }

        // Initialize audio manager (lightweight, safe on main thread)
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Run heavy initialization on a background thread to avoid blocking the UI
        Thread {
            initWebRtcInfrastructure()

            // Add STUN server
            val stun = IceServer.builder(
                String.format("stun:stun.kinesisvideo.%s.amazonaws.com:443", region)
            ).createIceServer()
            peerIceServers.add(stun)

            // Continue with connection setup (already on background thread)
            if (iceServers.isNotEmpty()) {
                // ICE servers already available (e.g. stored session) — sequential flow
                peerIceServers.addAll(iceServers)
                createLocalPeerConnection()
                if (WebRtcConstants.SEND_AUDIO_BY_DEFAULT) addLocalAudioTrack()
                initWsConnection()
            } else if (dataEndpoint != null) {
                // Fetch ICE servers in parallel with WebSocket signaling connect
                val role = if (isMaster) ChannelRole.MASTER else ChannelRole.VIEWER
                val executor = Executors.newFixedThreadPool(2)

                val iceFuture = executor.submit<List<IceServer>> {
                    try {
                        Log.d(TAG, "Fetching ICE servers in parallel with signaling connect")
                        WebRtcChannelInfoHelper.fetchIceServersBlocking(region, channelArn, dataEndpoint, role)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch ICE servers: ${e.message}")
                        emptyList()
                    }
                }

                executor.execute {
                    try {
                        // Connect signaling WebSocket (blocking ~500ms-2s)
                        connectSignaling()

                        // Get ICE servers (typically already done, hidden behind WS latency)
                        val fetchedIceServers = iceFuture.get()
                        if (fetchedIceServers.isEmpty()) {
                            Log.w(TAG, "No TURN servers available, proceeding with STUN only")
                        }
                        peerIceServers.addAll(fetchedIceServers)

                        // Create peer connection (fast, needs ICE servers)
                        createLocalPeerConnection()
                        if (WebRtcConstants.SEND_AUDIO_BY_DEFAULT) addLocalAudioTrack()

                        // Create and send SDP offer
                        if (localPeer != null && client?.isOpen == true) {
                            createSdpOffer()
                        } else {
                            val msg = if (localPeer == null) "Peer connection not initialized"
                                      else "Failed to connect to signaling service"
                            onError?.invoke(msg)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during parallel WebRTC setup: ${e.message}", e)
                        onError?.invoke("WebRTC setup failed: ${e.message}")
                    } finally {
                        executor.shutdown()
                    }
                }
            } else {
                // Fallback: no ICE servers and no dataEndpoint — just connect with STUN
                Log.w(TAG, "No ICE servers or dataEndpoint provided, using STUN only")
                createLocalPeerConnection()
                if (WebRtcConstants.SEND_AUDIO_BY_DEFAULT) addLocalAudioTrack()
                initWsConnection()
            }
        }.start()
    }

    /**
     * Start a WebRTC session using local network signaling instead of KVS WebSocket.
     * SDP/ICE exchange happens over the device's HTTP local control transport using protobuf.
     * No STUN/TURN servers are used — LAN-only ICE candidates.
     */
    fun startLocal(
        localDevice: EspLocalDevice,
        surfaceViewRenderer: SurfaceViewRenderer,
        clientId: String? = null
    ) {
        synchronized(stateLock) {
            if (!canStart()) {
                Log.w(TAG, "startLocal() called in state $state; ignoring")
                return
            }
            state = ManagerState.STARTING
            deferredStopRequested = false
            Log.d(TAG, "state: -> STARTING (startLocal)")
        }
        if (rootEglBase != null) {
            Log.w(TAG, "WebRTC already started")
            return
        }

        ensureExecutorsAlive()

        this.remoteView = surfaceViewRenderer
        lastStartParams = CachedStartParams.Local(localDevice, surfaceViewRenderer, clientId)
        attachRenderer(surfaceViewRenderer)
        mClientId = clientId ?: "local-app-${UUID.randomUUID().toString().substring(0, 8)}"

        // Initialize audio manager
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        Thread {
            initWebRtcInfrastructure()

            // No STUN/TURN for LAN-only signaling — peerIceServers stays empty

            // Create peer connection (no ICE servers for LAN)
            createLocalPeerConnection()
            if (WebRtcConstants.SEND_AUDIO_BY_DEFAULT) addLocalAudioTrack()

            // Create local signaling client
            localClient = LocalSignalingClient(
                localDevice = localDevice,
                peerId = mClientId!!,
                onSdpAnswer = { sdp ->
                    Log.d(TAG, "Local signaling: SDP answer received")
                    sdpAnswerReceived = true
                    offerRetryHandler.removeCallbacksAndMessages(null)
                    val sdpAnswer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    localPeer?.setRemoteDescription(
                        object : KinesisVideoSdpObserver() {
                            override fun onCreateFailure(error: String) {
                                super.onCreateFailure(error)
                                Log.e(TAG, "Failed to set remote description: $error")
                            }
                        },
                        sdpAnswer
                    )
                    // Local signaling adds remote candidates straight to localPeer (see
                    // onIceCandidate below) and never queues them, so there is nothing to
                    // flush here.
                },
                onIceCandidate = { candidateJson ->
                    Log.d(TAG, "Local signaling: ICE candidate received")
                    val candidate = LocalSignalingClient.parseIceCandidateJson(candidateJson)
                    if (candidate != null) {
                        localPeer?.addIceCandidate(candidate)
                    } else {
                        Log.e(TAG, "Failed to parse ICE candidate from local signaling")
                    }
                },
                onError = { error ->
                    Log.e(TAG, "Local signaling error: $error")
                    onError?.invoke(error)
                }
            )

            // Create and send SDP offer
            if (localPeer != null) {
                createSdpOffer()
            } else {
                onError?.invoke("Peer connection not initialized")
            }
        }.start()
    }

    /**
     * Initialize EGL, PeerConnectionFactory, and SurfaceViewRenderer.
     * Shared between [start] (KVS) and [startLocal] (local signaling).
     * Must be called on a background thread.
     */
    private fun initWebRtcInfrastructure() {
        rootEglBase = EglBase.create()

        ensureFactoryInitialized(context)

        val vdf = LowLatencyDefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext)
        val vef = BaselineDefaultVideoEncoderFactory(
            rootEglBase!!.eglBaseContext,
            ENABLE_INTEL_VP8_ENCODER,
            ENABLE_H264_HIGH_PROFILE
        )

        val adm = JavaAudioDeviceModule.builder(context.applicationContext)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .createAudioDeviceModule()
        audioDeviceModule = adm

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(vdf)
            .setVideoEncoderFactory(vef)
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()

        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)

        // Kick off SurfaceViewRenderer init on main thread (required for view ops)
        // but DON'T block — peer connection + offer creation proceed in parallel.
        // The renderer init is posted to the main thread handler first, and the
        // video sink attachment (addRemoteStreamToVideoView) is also posted to the
        // main thread later — FIFO ordering guarantees the renderer is ready.
        Handler(Looper.getMainLooper()).post {
            try {
                remoteView?.release()
                remoteView?.init(rootEglBase!!.eglBaseContext, null)
                remoteView?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                remoteView?.setMirror(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing SurfaceViewRenderer: ${e.message}")
            }
        }
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
                if (localClient != null) {
                    Log.d(TAG, "Sending IceCandidate via local signaling")
                    localClient?.sendIceCandidate(iceCandidate)
                } else {
                    val message = createIceCandidateMessage(iceCandidate)
                    Log.d(TAG, "Sending IceCandidate to remote peer $iceCandidate")
                    client?.sendIceCandidate(message)
                }
            }

            override fun onAddStream(mediaStream: MediaStream) {
                super.onAddStream(mediaStream)
                Log.d(TAG, "onAddStream: Adding remote stream to the view")
                addRemoteStreamToVideoView(mediaStream)
            }

            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out MediaStream>) {
                super.onAddTrack(receiver, streams)
                val track = receiver.track()
                Log.d(TAG, "onAddTrack: kind=${track?.kind()}, id=${track?.id()}, state=${track?.state()?.name}")
                if (track is AudioTrack) {
                    Log.d(TAG, "onAddTrack: Remote audio track received")
                    remoteAudioTrack = track
                    val shouldPlay = !isIncomingAudioMuted
                    track.setEnabled(shouldPlay)
                    Log.d(TAG, "remoteAudioTrack via onAddTrack: playing=$shouldPlay")
                    if (shouldPlay) {
                        audioManager?.mode = AudioManager.MODE_NORMAL
                        audioManager?.isSpeakerphoneOn = true
                    }
                }
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                super.onIceConnectionChange(iceConnectionState)

                // Don't process callbacks if we're already tearing down
                if (state == ManagerState.STOPPING || state == ManagerState.STOPPED) {
                    Log.d(TAG, "Ignoring ICE connection change - state=$state")
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
                        // (state guard already checked above at method entry.)
                        Thread({ stop() }, "ice-failed-stop").start()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "ICE connection disconnected - connection may recover, not tearing down")
                        // DISCONNECTED is often transient (mobile NAT timeout, brief link
                        // glitch, RTT spike). On real hardware we observe ICE going
                        // DISCONNECTED → CONNECTED again in 300-500 ms — well below the
                        // FAILED timeout. Don't notify the UI as "failed" here; the
                        // user's app shows "peer failed" even though the peer is fine.
                        // Only ICE FAILED below is terminal and gets the false callback
                        // (via the stop() path that fires onConnectionStateChanged(false)
                        // on actual disconnect).
                    }
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        if (!isStreamActive && state != ManagerState.STOPPING && state != ManagerState.STOPPED) {
                            streamStartTime = System.currentTimeMillis()
                            isStreamActive = true
                            Log.i(TAG, "Stream started - duration tracking began")
                            startStatsCollection()
                            // Peer connected — nothing left to poll for.
                            localClient?.stopPolling()
                        }
                        if (pendingRenegotiation) {
                            pendingRenegotiation = false
                            Log.i(TAG, "Flushing deferred renegotiation after CONNECTED")
                            renegotiate()
                        }
                        if (state != ManagerState.STOPPING && state != ManagerState.STOPPED) {
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
        // Peer connection initialized — STARTING -> RUNNING (or honor deferred stop)
        if (localPeer != null) {
            transitionToRunningIfStarting()
        }
    }

    /**
     * Connect the signaling WebSocket only (no SDP offer creation).
     * Used in the parallel flow where PeerConnection is created after ICE servers arrive.
     */
    private fun connectSignaling() {
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
                sdpAnswerReceived = true
                offerRetryHandler.removeCallbacksAndMessages(null)
                val sdp = Event.parseSdpEvent(answerEvent)
                val sdpAnswer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                // Snapshot localPeer once: stop() can null it on the main thread
                // while this answer is processed on the signaling-worker thread.
                val peer = localPeer
                if (peer == null) {
                    Log.w(TAG, "SDP answer arrived after localPeer was cleared; ignoring")
                    return
                }
                val senderClientId = answerEvent.senderClientId
                Log.d(TAG, "Answer Client ID: $senderClientId")
                // The camera MASTER answers with a null senderClientId. Normalize it so the
                // peer is always registered and queued remote candidates are flushed.
                val peerKey = peerKey(senderClientId)
                // setRemoteDescription is async: register the peer and flush queued
                // candidates only from onSetSuccess(), otherwise addIceCandidate() runs
                // while remoteDescription is still null and libwebrtc drops the candidate.
                peer.setRemoteDescription(
                    object : KinesisVideoSdpObserver() {
                        override fun onSetSuccess() {
                            super.onSetSuccess()
                            // Register the peer first, then flip the applied-flag and flush
                            // any trickled candidates queued before the answer was applied.
                            peerConnectionFoundMap[peerKey] = peer
                            pendingIceCandidateQueue.onRemoteDescriptionApplied(peerKey)
                        }

                        override fun onSetFailure(error: String) {
                            super.onSetFailure(error)
                            Log.e(TAG, "Failed to set remote description: $error")
                        }

                        override fun onCreateFailure(error: String) {
                            super.onCreateFailure(error)
                            Log.e(TAG, "Failed to set remote description: $error")
                        }
                    },
                    sdpAnswer
                )
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
            Log.d(TAG, "Signaling connection ${if (client?.isOpen == true) "Successful" else "Failed"}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception with websocket client: $e")
            onError?.invoke("WebSocket exception: ${e.message}")
        }
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
                sdpAnswerReceived = true
                offerRetryHandler.removeCallbacksAndMessages(null)

                // Snapshot localPeer once. stop() may null it on the main thread between
                // any two reads on this signaling-worker thread.
                val peer = localPeer
                if (peer == null) {
                    Log.w(TAG, "onSdpAnswer: localPeer is null (session stopping); ignoring answer")
                    return
                }

                val sdp = Event.parseSdpEvent(answerEvent)
                val sdpAnswer = SessionDescription(SessionDescription.Type.ANSWER, sdp)

                Log.d(TAG, "Answer Client ID: ${answerEvent.senderClientId}")
                // AWS Kinesis Video Signaling can deliver an SDP answer with a null
                // senderClientId (observed when the master is the camera/MASTER role and
                // doesn't fill it). Normalize it to the same key used when queueing
                // remote candidates, so the peer is registered and the pending
                // (trickled) candidates are actually added to the PeerConnection.
                val peerKey = peerKey(answerEvent.senderClientId)

                // setRemoteDescription is async: register the peer and flush queued
                // candidates only from onSetSuccess(), otherwise addIceCandidate() runs
                // while remoteDescription is still null and libwebrtc drops the candidate
                // (fatal for relay-only, where the single TURN candidate is trickled).
                peer.setRemoteDescription(
                    object : KinesisVideoSdpObserver() {
                        override fun onSetSuccess() {
                            super.onSetSuccess()
                            // Register the peer first, then flip the applied-flag and flush
                            // any trickled candidates queued before the answer was applied.
                            peerConnectionFoundMap[peerKey] = peer
                            pendingIceCandidateQueue.onRemoteDescriptionApplied(peerKey)
                        }

                        override fun onSetFailure(error: String) {
                            super.onSetFailure(error)
                            Log.e(TAG, "Failed to set remote description: $error")
                        }

                        override fun onCreateFailure(error: String) {
                            super.onCreateFailure(error)
                            Log.e(TAG, "Failed to set remote description: $error")
                        }
                    },
                    sdpAnswer
                )
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
        if (WebRtcConstants.OFFER_AUDIO) {
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        localPeer?.createOffer(object : KinesisVideoSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer?.setLocalDescription(object : KinesisVideoSdpObserver() {}, sessionDescription)

                if (localClient != null) {
                    Log.d(TAG, "SDP Offer created, sending via local signaling")
                    localClient?.sendSdpOffer(sessionDescription.description)
                    // No scheduleOfferRetry() — LocalSignalingClient has its own answer timeout
                } else {
                    val message = Message.createOfferMessage(sessionDescription, mClientId ?: "")
                    if (client?.isOpen == true) {
                        client?.sendSdpOffer(message)
                        Log.d(TAG, "SDP Offer created and sent")
                        scheduleOfferRetry()
                    } else {
                        Log.e(TAG, "Cannot send SDP offer: WebSocket connection is not open")
                        onError?.invoke("WebSocket connection lost")
                    }
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
        Log.d(TAG, "addRemoteStream: videoTracks=${stream.videoTracks?.size ?: 0}, audioTracks=${stream.audioTracks?.size ?: 0}")

        // Store references to tracks (don't overwrite existing with null)
        if (videoTrack != null) this.remoteVideoTrack = videoTrack
        if (audioTrack != null) this.remoteAudioTrack = audioTrack

        if (audioTrack != null) {
            val shouldPlay = !isIncomingAudioMuted
            audioTrack.setEnabled(shouldPlay)
            Log.d(TAG, "remoteAudioTrack received: State=${audioTrack.state().name}, playing=$shouldPlay")
            if (shouldPlay) {
                audioManager?.mode = AudioManager.MODE_NORMAL
                audioManager?.isSpeakerphoneOn = true
            }
        }

        if (videoTrack != null) {
            val view = remoteView
            if (view == null) {
                Log.e(TAG, "Cannot add video sink: remoteView is null")
                onError?.invoke("Video view not initialized")
                return
            }

            Handler(Looper.getMainLooper()).post {
                try {
                    // Double-check view is still valid
                    val currentView = remoteView
                    if (currentView == null) {
                        Log.e(TAG, "remoteView became null before adding sink")
                        return@post
                    }

                    // Defensive init: the createLocalPeerConnection path queues
                    // remoteView.init() on the main thread, and onAddStream may
                    // queue this addSink BEFORE that init has executed. If we
                    // addSink to a not-yet-init'd renderer, EglRenderer's frame
                    // pump runs (Frames received counts) but renderFrameOnRender
                    // Thread drops every frame because eglBase has no Surface
                    // ⇒ FPS overlay shows but viewport stays black. init() is
                    // idempotent if we catch IllegalStateException from "already
                    // initialized".
                    val egl = rootEglBase
                    if (egl != null) {
                        try {
                            currentView.init(egl.eglBaseContext, null)
                            Log.d(TAG, "Defensive init of remoteView before addSink")
                        } catch (e: IllegalStateException) {
                            // Already initialized — that's the expected path
                        }
                    } else {
                        Log.w(TAG, "rootEglBase is null at addSink time; renderer cannot bind EGL")
                    }

                    Log.d(TAG, "remoteVideoTrackId=${videoTrack.id()} videoTrackState=${videoTrack.state()}")
                    videoTrack.addSink(currentView)
                    // Independent count-only sink for the surface watchdog (decode side).
                    try { videoTrack.addSink(countingReceiveSink) } catch (_: Exception) {}
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

    /**
     * The remote camera (MASTER) can send its SDP answer and its trickled ICE candidates
     * with a null/empty senderClientId. The peer/pending maps are keyed by a non-null
     * String, so normalize a missing id to one stable key. This normalization MUST be
     * used everywhere the maps are keyed, otherwise queued candidates are never flushed.
     * This is a single-session viewer (one master), so collapsing to one key is fine.
     */
    private fun peerKey(senderClientId: String?): String =
        pendingIceCandidateQueue.peerKey(senderClientId)

    private fun checkAndAddIceCandidate(message: Event, iceCandidate: IceCandidate) {
        // Delegates the "add now vs. queue until the answer is applied" decision to
        // pendingIceCandidateQueue; the localPeer safety net stays gated on the
        // remote-description-applied flag inside the queue.
        pendingIceCandidateQueue.offer(message.senderClientId, iceCandidate)
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

    /**
     * Called from the async setup path after createLocalPeerConnection() has run.
     * Moves STARTING -> RUNNING, or honors a deferred-stop request that arrived
     * while we were still in STARTING.
     */
    private fun transitionToRunningIfStarting() {
        val wantStop: Boolean
        val wantWatchdog: Boolean
        synchronized(stateLock) {
            if (state != ManagerState.STARTING) return
            if (deferredStopRequested) {
                deferredStopRequested = false
                wantStop = true
                wantWatchdog = false
            } else {
                state = ManagerState.RUNNING
                wantStop = false
                wantWatchdog = true
                Log.d(TAG, "state: STARTING -> RUNNING")
            }
        }
        if (wantWatchdog) {
            // Successful transition to RUNNING — any prior restart-in-flight is resolved.
            restartInFlight = false
            startSurfaceWatchdog()
        }
        if (wantStop) {
            Log.d(TAG, "Deferred stop honored after STARTING")
            // run stop on a fresh thread so we don't recurse on the signaling thread
            Thread { stop() }.start()
        }
    }

    fun stop() {
        val transitionedToStopping: Boolean
        synchronized(stateLock) {
            when (state) {
                ManagerState.IDLE -> {
                    Log.d(TAG, "stop() called in IDLE; no-op")
                    return
                }
                ManagerState.STOPPED -> {
                    Log.d(TAG, "stop() called in STOPPED; no-op")
                    return
                }
                ManagerState.STOPPING -> {
                    Log.w(TAG, "stop() already in progress; ignoring duplicate call")
                    return
                }
                ManagerState.STARTING -> {
                    Log.d(TAG, "stop() during STARTING; deferring until peer init returns")
                    deferredStopRequested = true
                    return
                }
                ManagerState.RUNNING -> {
                    state = ManagerState.STOPPING
                    transitionedToStopping = true
                    Log.d(TAG, "state: RUNNING -> STOPPING")
                }
            }
        }
        if (!transitionedToStopping) return

        try {
            performStopCleanup()
        } finally {
            synchronized(stateLock) {
                state = ManagerState.STOPPED
            }
            Log.d(TAG, "state: STOPPING -> STOPPED")
            // Fire onTerminallyStopped iff this stop is terminal — either an explicit
            // user-initiated stop or no replacement is in flight. Reset userInitiatedStop
            // so a follow-on stop() does not inherit the flag.
            val wasUserInitiated = userInitiatedStop
            val fireTerminal = userInitiatedStop || !restartInFlight
            val callback = onTerminallyStopped
            userInitiatedStop = false
            if (fireTerminal && callback != null) {
                try {
                    Log.d(TAG, "Firing onTerminallyStopped (userInitiated=$wasUserInitiated, restartInFlight=$restartInFlight)")
                    callback.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking onTerminallyStopped: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Suppressing onTerminallyStopped (restartInFlight=$restartInFlight)")
            }
        }
    }

    private fun performStopCleanup() {
        Log.d(TAG, "Stopping WebRTC connection")

        // Stop surface watchdog
        stopSurfaceWatchdog()

        // Cancel any pending idle-stop and clear renderer set
        idleStopHandler.removeCallbacks(idleStopRunnable)
        synchronized(attachLock) {
            // Remove FrameListeners and clear back-references before dropping the set
            attachedRenderers.forEach { r ->
                frameListenerByRenderer.remove(r)?.let { listener ->
                    try { r.removeFrameListener(listener) } catch (_: Exception) {}
                }
                if (r is FlippableSurfaceViewRenderer && r.attachedManager === this) {
                    r.attachedManager = null
                }
            }
            attachedRenderers.clear()
            renderedFrameCounterByRenderer.clear()
        }

        // Detach the count-only watchdog sink from the remote video track if still attached
        try { remoteVideoTrack?.removeSink(countingReceiveSink) } catch (_: Exception) {}

        // Stop stats collection before anything else
        stopStatsCollection()
        onStatsUpdated = null
        onMediaToggleChanged = null
        onIncomingAudioMuteChanged = null

        isStreamActive = false
        // Clear the deferred-renegotiation flag so a reused instance does not fire a
        // stale renegotiation on the next session's first CONNECTED.
        pendingRenegotiation = false

        // Shut down the media-toggle executor before disposing native objects, so a
        // queued enable/disable task cannot run addTrack/removeTrack against an
        // already-disposed peer/factory (use-after-free), and the worker thread does
        // not leak across the per-session manager instances.
        mediaOpsExecutor.shutdownNow()
        // Cancel any pending offer retry
        offerRetryHandler.removeCallbacksAndMessages(null)
        sdpAnswerReceived = false
        pendingIceCandidateQueue.reset()
        offerAttempt = 0

        // Clean up local signaling client
        localClient?.disconnect()
        localClient = null

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
                val handler = Handler(Looper.getMainLooper())
                if (Looper.myLooper() == Looper.getMainLooper()) {
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
                    // Wait up to 1 second for sink removal. If interrupted, restore the
                    // flag for downstream code but CONTINUE through the rest of stop() —
                    // bailing here leaves the ADM and factory alive and the mic hot.
                    try {
                        latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.w(TAG, "Interrupted waiting for sink removal; continuing cleanup", e)
                        Thread.currentThread().interrupt()
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

        // Drain executors that may still submit JNI work touching the factory. Without
        // this drain, a signaling thread can submit to mediaOpsExecutor after dispose()
        // and JNI lands on a freed factory (SIGABRT in signaling_thread). Same risk for
        // statsExecutor calling peer.getStats() against a half-disposed graph.
        drainExecutor(mediaOpsExecutor, MEDIA_OPS_THREAD_NAME)
        drainExecutor(statsExecutor, STATS_THREAD_NAME)

        // Release the Java-side AudioDeviceModule explicitly. PeerConnectionFactory.dispose()
        // only drops its reference; the ADM's native capture/playback threads keep running
        // otherwise and the mic stays hot.
        try {
            audioDeviceModule?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioDeviceModule: $e", e)
        }
        audioDeviceModule = null

        // Dispose PeerConnectionFactory so its native AudioDeviceModule stops capturing
        // and playing audio. Previously we left it alive to speed up restart, but that
        // leaked audio activity into the background; restartIfStopped() now handles the
        // re-creation cost (hundreds of ms).
        try {
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing PeerConnectionFactory: $e", e)
        }
        peerConnectionFactory = null

        // Reset audio manager
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio manager: $e", e)
        }
        audioManager = null

        // Clear maps
        peerConnectionFoundMap.clear()
        peerIceServers.clear()

        // Invoke callback on main thread if available (callbacks already cleared above, use stored reference)
        try {
            connectionCallback?.let { callback ->
                Handler(Looper.getMainLooper()).post {
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

        Log.d(TAG, "WebRTC connection stopped")
    }

    fun isActive(): Boolean {
        return rootEglBase != null && (client?.isOpen == true || localClient?.isOpen() == true)
    }

    private fun ensureExecutorsAlive() {
        if (mediaOpsExecutor.isShutdown) {
            mediaOpsExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, MEDIA_OPS_THREAD_NAME) }
        }
        if (statsExecutor.isShutdown) {
            statsExecutor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, STATS_THREAD_NAME) }
        }
    }

    private fun drainExecutor(executor: java.util.concurrent.ExecutorService, name: String) {
        // Self-drain guard: if we're already running on the executor's worker thread,
        // awaitTermination can never succeed (the thread can't terminate while it's
        // running this code). Skip the wait and just shutdownNow — the current task
        // returns naturally and no new work will be accepted.
        if (Thread.currentThread().name == name) {
            Log.w(TAG, "drainExecutor: called from $name thread; skipping awaitTermination")
            try { executor.shutdownNow() } catch (_: Exception) {}
            return
        }
        try {
            executor.shutdown()
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                Log.w(TAG, "$name did not terminate within 2s, forcing shutdownNow")
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted draining $name; forcing shutdownNow")
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Log.e(TAG, "Error draining $name: ${e.message}", e)
            try { executor.shutdownNow() } catch (_: Exception) {}
        }
    }

    /**
     * If this session has been stopped (e.g. by the process-level ON_STOP observer)
     * and we have cached start parameters, release the cached renderer and replay
     * the original start() / startLocal() call. Intended to be called from an
     * ON_RESUME observer in the UI that owns the manager. Returns true if a
     * restart was attempted, false if the session is still active or no cached
     * params exist.
     */
    /**
     * Explicit user-initiated stop. Always fires onTerminallyStopped regardless of
     * any in-flight restart flag — i.e. "the user pressed Stop, the UI should idle".
     */
    fun userStop() {
        userInitiatedStop = true
        Log.d(TAG, "userStop(): user-initiated stop requested")
        stop()
    }

    /**
     * Mark a fallback / retry as in-flight on this manager. Set BEFORE calling stop()
     * when a replacement session is about to start. Cleared automatically on
     * successful transition to RUNNING; callers may also clear explicitly.
     */
    fun markRestartInFlight() {
        restartInFlight = true
        Log.d(TAG, "Restart-in-flight flag set; next stop() will not fire onTerminallyStopped")
    }

    fun clearRestartInFlight() {
        restartInFlight = false
    }

    fun restartIfStopped(): Boolean {
        synchronized(stateLock) {
            if (!canRestart()) {
                Log.d(TAG, "restartIfStopped: state=$state, not STOPPED — refusing")
                return false
            }
        }
        val params = lastStartParams ?: return false
        Log.d(TAG, "restartIfStopped: replaying last start params")
        return try {
            when (params) {
                is CachedStartParams.Kvs -> {
                    try { params.surfaceViewRenderer.release() } catch (e: Exception) {
                        Log.w(TAG, "Renderer release before restart failed: ${e.message}")
                    }
                    start(
                        channelArn = params.channelArn,
                        streamArn = params.streamArn,
                        wssEndpoint = params.wssEndpoint,
                        webrtcEndpoint = params.webrtcEndpoint,
                        region = params.region,
                        iceServers = params.iceServers,
                        dataEndpoint = params.dataEndpoint,
                        surfaceViewRenderer = params.surfaceViewRenderer,
                        isMaster = params.isMaster,
                        clientId = params.clientId
                    )
                }
                is CachedStartParams.Local -> {
                    try { params.surfaceViewRenderer.release() } catch (e: Exception) {
                        Log.w(TAG, "Renderer release before restart failed: ${e.message}")
                    }
                    startLocal(
                        localDevice = params.localDevice,
                        surfaceViewRenderer = params.surfaceViewRenderer,
                        clientId = params.clientId
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "restartIfStopped failed: ${e.message}", e)
            false
        }
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
    // Recreated by start()/startLocal() if previously drained by stop().
    private var statsExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, STATS_THREAD_NAME) }
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
                            // Only process video inbound-rtp, skip audio
                            if (m["kind"]?.toString() != "video") continue
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
     * Register a SurfaceViewRenderer as an active UI consumer of this session.
     * Cancels any pending idle-stop. Safe to call multiple times for the same renderer.
     */
    /**
     * Direct frame-render notification from FlippableSurfaceViewRenderer.onFrame.
     * Bypasses EglRenderer.addFrameListener which doesn't fire on this WebRTC build.
     * Bumps the renderer's per-renderer rendered-frame counter used by the surface
     * watchdog to detect a stuck render path.
     */
    fun notifyRendered(renderer: SurfaceViewRenderer) {
        renderedFrameCounterByRenderer[renderer]?.incrementAndGet()
    }

        fun attachRenderer(renderer: SurfaceViewRenderer) {
        synchronized(attachLock) {
            if (attachedRenderers.add(renderer)) {
                // Set stable back-reference so the renderer's surfaceDestroyed can
                // detach without depending on a Compose-captured manager.
                if (renderer is FlippableSurfaceViewRenderer) {
                    renderer.attachedManager = this
                }
                // Per-renderer rendered-frame counter via EglRenderer.FrameListener
                renderedFrameCounterByRenderer.putIfAbsent(
                    renderer, AtomicLong(0L)
                )
                val listener = FrameListener {
                    renderedFrameCounterByRenderer[renderer]?.incrementAndGet()
                }
                frameListenerByRenderer[renderer] = listener
                try {
                    renderer.addFrameListener(listener, 1.0f)
                } catch (e: Exception) {
                    Log.w(TAG, "addFrameListener failed for renderer ${renderer.hashCode()}: ${e.message}")
                }
                Log.d(TAG, "attachRenderer: count=${attachedRenderers.size}")
            }
        }
        idleStopHandler.removeCallbacks(idleStopRunnable)
        // New attach = fresh UI engagement; give the watchdog a clean budget.
        watchdogBadTicks.set(0)
    }

    /**
     * Deregister a SurfaceViewRenderer. When no renderers remain attached,
     * schedule a delayed stop so brief transitions don't tear the session down.
     */
    fun detachRenderer(renderer: SurfaceViewRenderer) {
        val isEmpty = synchronized(attachLock) {
            val removed = attachedRenderers.remove(renderer)
            if (removed) {
                // Remove the per-renderer FrameListener and counter
                frameListenerByRenderer.remove(renderer)?.let { listener ->
                    try { renderer.removeFrameListener(listener) } catch (_: Exception) {}
                }
                renderedFrameCounterByRenderer.remove(renderer)
                // Clear back-reference only if this manager still owns it
                if (renderer is FlippableSurfaceViewRenderer && renderer.attachedManager === this) {
                    renderer.attachedManager = null
                }
                Log.d(TAG, "detachRenderer: count=${attachedRenderers.size}")
            }
            attachedRenderers.isEmpty()
        }
        if (isEmpty) {
            val curState = state
            if (curState == ManagerState.RUNNING) {
                idleStopHandler.removeCallbacks(idleStopRunnable)
                idleStopHandler.postDelayed(idleStopRunnable, IDLE_STOP_DELAY_MS)
                Log.d(TAG, "No renderers attached; scheduled stop in ${IDLE_STOP_DELAY_MS}ms")
            } else {
                Log.d(TAG, "No renderers attached but state=$curState — NOT scheduling idle stop (only valid from RUNNING)")
            }
        }
    }

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
            if (Looper.myLooper() == Looper.getMainLooper()) {
                transferVideoTrackInternal(videoTrack, oldView, newRenderer)
            } else {
                Handler(Looper.getMainLooper()).post {
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

            // Verify rootEglBase is available
            if (rootEglBase == null) {
                Log.e(TAG, "Root EglBase is null - cannot transfer video")
                throw IllegalStateException("Root EglBase is null")
            }

            // Don't transfer if it's already the current renderer
            if (remoteView == newRenderer) {
                Log.w(TAG, "New renderer is already the current remoteView - skipping transfer")
                return
            }

            // Configure renderer for display
            try {
                newRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                newRenderer.setMirror(false)
            } catch (e: Exception) {
                Log.w(TAG, "Error setting renderer properties: ${e.message} - continuing anyway")
            }

            // Add new sink BEFORE removing old ones to avoid a sinkless gap.
            // WebRTC VideoTrack supports multiple simultaneous sinks, so this is safe.
            // Without this, the sender gets back-pressured during the gap which causes
            // slow data send on the ESP device and a black screen on the app.
            try {
                Log.d(TAG, "Adding video sink to new renderer (newRenderer=${newRenderer.hashCode()})...")
                val previousRemoteView = remoteView
                videoTrack.addSink(newRenderer)
                attachRenderer(newRenderer)
                Log.d(TAG, "Video sink added to new renderer")

                // Now remove old sinks (new sink is already receiving frames)
                // Skip if oldView is the same as newRenderer (e.g. landscape→portrait return)
                if (oldView != null && oldView != newRenderer) {
                    try {
                        videoTrack.removeSink(oldView)
                        detachRenderer(oldView)
                        Log.d(TAG, "Removed old sink (oldView=${oldView.hashCode()})")
                    } catch (e: Exception) {
                        Log.w(TAG, "Note removing sink from old view: ${e.message}")
                    }
                }

                if (previousRemoteView != null && previousRemoteView != oldView && previousRemoteView != newRenderer) {
                    try {
                        videoTrack.removeSink(previousRemoteView)
                        detachRenderer(previousRemoteView)
                        Log.d(TAG, "Removed sink from previous remoteView (${previousRemoteView.hashCode()})")
                    } catch (e: Exception) {
                        Log.w(TAG, "Note removing sink from previous remoteView: ${e.message}")
                    }
                }

                remoteView = newRenderer
                Log.d(TAG, "Video transfer complete - no sinkless gap")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding video sink to new renderer: ${e.message}", e)
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
