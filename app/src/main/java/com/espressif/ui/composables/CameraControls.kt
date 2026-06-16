package com.espressif.ui.composables

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.RendererCommon
import com.espressif.ui.webrtc.WebRtcViewportManager
import com.espressif.ui.webrtc.WebRtcChannelInfoHelper
import com.espressif.ui.webrtc.WebRtcChannelInfo
import com.amazonaws.services.kinesisvideo.model.ChannelRole
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.rainmaker.R
import com.espressif.ui.activities.WebRtcConfigActivity
import com.espressif.ui.activities.WebRtcActivity
import com.espressif.webrtc.WebRtcConstants
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.espressif.ui.webrtc.WebRtcStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private const val TAG = "CAM"

/**
 * Suspends until AWS credentials (region) are available or timeout is reached.
 * @return true if credentials are available, false if timeout occurred
 */
private suspend fun waitForCredentials(
    maxWaitTime: Int = 10000,
    pollInterval: Long = 500L
): Boolean {
    if (!EspApplication.region.isNullOrEmpty()) {
        return true
    }

    Log.d(TAG, "Waiting for credentials to be fetched...")
    var waitTime = 0

    while (EspApplication.region.isNullOrEmpty() && waitTime < maxWaitTime) {
        delay(pollInterval)
        waitTime += pollInterval.toInt()
    }

    return !EspApplication.region.isNullOrEmpty()
}

/**
 * Composable dialog showing detailed WebRTC stats, auto-refreshes every second.
 */
@Composable
fun WebRtcStatsDialog(
    stats: WebRtcStats,
    onDismiss: () -> Unit
) {
    val packetLossPercent = if (stats.totalPacketsReceived + stats.totalPacketsLost > 0) {
        (stats.totalPacketsLost * 100.0) / (stats.totalPacketsReceived + stats.totalPacketsLost)
    } else 0.0
    val bytesReceivedMB = stats.totalBytesReceived / (1024.0 * 1024.0)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(stringResource(R.string.camera_stats_title), color = Color.White, style = MaterialTheme.typography.titleSmall)

            Text(stringResource(R.string.camera_stats_video), color = Color.Gray, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp))
            StatsRow(stringResource(R.string.camera_stats_resolution), "${stats.frameWidth} x ${stats.frameHeight}",
                stringResource(R.string.camera_stats_current_fps), String.format("%.1f", stats.currentFps))
            StatsRow(stringResource(R.string.camera_stats_received_fps), String.format("%.1f", stats.receivedFps),
                stringResource(R.string.camera_stats_dropped_fps), String.format("%.1f", stats.droppedFps))
            StatsRow(stringResource(R.string.camera_stats_frames_dropped), "${stats.totalFramesDropped}",
                stringResource(R.string.camera_stats_codec), stats.videoCodec)

            Text(stringResource(R.string.camera_stats_network), color = Color.Gray, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp))
            StatsRow(stringResource(R.string.camera_stats_bitrate), "${stats.currentBitrateKbps} kbps",
                stringResource(R.string.camera_stats_total_data), String.format("%.2f MB", bytesReceivedMB))
            StatsRow(stringResource(R.string.camera_stats_packets_rx), "${stats.totalPacketsReceived}",
                stringResource(R.string.camera_stats_packets_lost), "${stats.totalPacketsLost}")
            StatsRow(stringResource(R.string.camera_stats_loss_percent), String.format("%.2f%%", packetLossPercent),
                stringResource(R.string.camera_stats_jitter), String.format("%.2f ms", stats.jitterMs))
        }
    }
}

@Composable
private fun StatsRow(label1: String, value1: String, label2: String, value2: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label1, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            Text(value1, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label2, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            Text(value2, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Unified video player composable used in both portrait viewport and landscape modes.
 *
 * Features:
 * - Tap to toggle play/stop overlay
 * - Long press for detailed stats dialog
 * - Orientation toggle button (portrait/landscape)
 * - Loading/error overlay states
 */
@Composable
fun WebRtcVideoPlayer(
    isPlaying: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    webRtcManager: WebRtcViewportManager?,
    onVideoPlay: () -> Unit,
    onVideoStop: () -> Unit,
    onFullscreen: () -> Unit,
    onSurfaceViewRendererChange: (SurfaceViewRenderer?) -> Unit,
    modifier: Modifier = Modifier,
    showOrientationToggle: Boolean = true,
    videoSessionKey: Int = 0
) {
    var showControls by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(WebRtcStats()) }
    // Read manager's toggle flags directly — all backed by mutableStateOf so this
    // composable recomposes when the manager flips state from any thread.
    // Shared source of truth across portrait + landscape; no local cache drift.
    val isVideoSendEnabled = webRtcManager?.isVideoSendEnabled ?: false
    val isAudioSendEnabled = webRtcManager?.isAudioSendEnabled ?: false
    val isIncomingAudioMuted = webRtcManager?.isIncomingAudioMuted ?: WebRtcConstants.INCOMING_AUDIO_MUTED_BY_DEFAULT

    // Subscribe to stats updates from the manager
    DisposableEffect(webRtcManager) {
        webRtcManager?.setOnStatsUpdated { newStats ->
            stats = newStats
        }
        onDispose {
            webRtcManager?.setOnStatsUpdated(null)
        }
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(modifier = modifier) {
        // Video content area
        Crossfade(
            targetState = isPlaying,
            animationSpec = tween(300),
            label = "video_crossfade",
            modifier = Modifier.fillMaxSize()
        ) { playing ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!playing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                    IconButton(
                        modifier = Modifier.size(64.dp),
                        onClick = onVideoPlay
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.camera_cd_play),
                            modifier = Modifier.size(64.dp),
                            tint = Color.White
                        )
                    }
                } else {
                    key(videoSessionKey) {
                        // Track this key-instance's renderer locally so the inner DisposableEffect
                        // releases the exact renderer this `key` block created. Without this,
                        // bumping videoSessionKey would drop the old SurfaceViewRenderer without
                        // a release() call — see CLAUDE.md: "SurfaceViewRenderer must have
                        // release() called before init() can be called again."
                        var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                    setMirror(false)
                                    localRenderer = this
                                    onSurfaceViewRendererChange(this)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { showControls = !showControls },
                                        onLongPress = { showStatsDialog = true }
                                    )
                                }
                        )
                        DisposableEffect(Unit) {
                            onDispose {
                                localRenderer?.let { renderer ->
                                    try {
                                        renderer.release()
                                    } catch (e: Exception) {
                                        Log.e("CameraControls", "Error releasing renderer on key bump: ${e.message}", e)
                                    }
                                }
                                localRenderer = null
                            }
                        }
                    }
                }
            }
        }

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.camera_connecting),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Error overlay
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Controls overlay (shown on tap)
        AnimatedVisibility(
            visible = isPlaying && showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                // Stop button in center
                IconButton(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp),
                    onClick = onVideoStop
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Stop icon (square)
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Black, RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Incoming audio mute/unmute button
                if (webRtcManager != null && WebRtcConstants.OFFER_AUDIO) {
                    IconButton(
                        onClick = { webRtcManager.toggleIncomingAudio() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(44.dp)
                            .background(
                                if (isIncomingAudioMuted) Color.Red.copy(alpha = 0.5f)
                                else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(22.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (isIncomingAudioMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (isIncomingAudioMuted) stringResource(R.string.camera_cd_unmute_audio) else stringResource(R.string.camera_cd_mute_audio),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Camera and Mic toggle buttons at the bottom
                if (WebRtcConstants.ENABLE_MEDIA_TOGGLE_UI) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Mic toggle
                        IconButton(
                            onClick = {
                                webRtcManager?.toggleAudioSending()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isAudioSendEnabled) Color.White.copy(alpha = 0.3f)
                                    else Color.Red.copy(alpha = 0.5f),
                                    RoundedCornerShape(22.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (isAudioSendEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = if (isAudioSendEnabled) stringResource(R.string.camera_cd_mute_mic) else stringResource(R.string.camera_cd_unmute_mic),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Camera toggle
                        IconButton(
                            onClick = {
                                webRtcManager?.toggleVideoSending()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isVideoSendEnabled) Color.White.copy(alpha = 0.3f)
                                    else Color.Red.copy(alpha = 0.5f),
                                    RoundedCornerShape(22.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (isVideoSendEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = if (isVideoSendEnabled) stringResource(R.string.camera_cd_disable_camera) else stringResource(R.string.camera_cd_enable_camera),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // FPS overlay (always visible when playing and stats available)
        if (isPlaying && stats.currentFps > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "fps: ${String.format("%.0f", stats.currentFps)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                if (stats.frameWidth > 0) {
                    Text(
                        text = "${stats.frameWidth} x ${stats.frameHeight}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Orientation toggle button (bottom-end)
        if (showOrientationToggle) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                onClick = onFullscreen,
                enabled = isPlaying && !isLoading
            ) {
                val iconTint = if (isPlaying && !isLoading) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }

                Box(modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.camera_cd_toggle_orientation),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(16.dp)
                            .rotate(-135f),
                        tint = iconTint
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .rotate(45f),
                        tint = iconTint
                    )
                }
            }
        }
    }

    // Stats dialog
    if (showStatsDialog) {
        WebRtcStatsDialog(
            stats = stats,
            onDismiss = { showStatsDialog = false }
        )
    }
}

/**
 * Full-screen controls overlay for landscape WebRtcActivity.
 * Hidden by default; tap to show controls (stop, mic toggle, camera toggle).
 * Auto-hides after 3 seconds.
 */
@Composable
fun LandscapeControlsOverlay(
    manager: WebRtcViewportManager?,
    onStop: () -> Unit
) {
    var showControls by remember { mutableStateOf(false) }
    // Read manager's toggle flags directly — same shared source of truth as portrait.
    val isVideoSendEnabled = manager?.isVideoSendEnabled ?: false
    val isAudioSendEnabled = manager?.isAudioSendEnabled ?: false
    val isIncomingAudioMuted = manager?.isIncomingAudioMuted ?: WebRtcConstants.INCOMING_AUDIO_MUTED_BY_DEFAULT

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        // Transparent full-screen tap target
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls }
                    )
                }
        ) {
            // Controls overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    // Stop button in center
                    IconButton(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp),
                        onClick = onStop
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = 0.8f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.camera_cd_stop),
                                modifier = Modifier.size(24.dp),
                                tint = Color.Black
                            )
                        }
                    }

                    // Incoming audio mute/unmute button
                    if (manager != null && WebRtcConstants.OFFER_AUDIO) {
                        IconButton(
                            onClick = { manager.toggleIncomingAudio() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = 16.dp)
                                .size(44.dp)
                                .background(
                                    if (isIncomingAudioMuted) Color.Red.copy(alpha = 0.5f)
                                    else Color.White.copy(alpha = 0.3f),
                                    RoundedCornerShape(22.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (isIncomingAudioMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = if (isIncomingAudioMuted) stringResource(R.string.camera_cd_unmute_audio) else stringResource(R.string.camera_cd_mute_audio),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Mic and Camera toggle buttons at the bottom
                    if (manager != null && WebRtcConstants.ENABLE_MEDIA_TOGGLE_UI) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    RoundedCornerShape(28.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Mic toggle
                            IconButton(
                                onClick = { manager.toggleAudioSending() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isAudioSendEnabled) Color.White.copy(alpha = 0.3f)
                                        else Color.Red.copy(alpha = 0.5f),
                                        RoundedCornerShape(22.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isAudioSendEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                                    contentDescription = if (isAudioSendEnabled) stringResource(R.string.camera_cd_mute_mic) else stringResource(R.string.camera_cd_unmute_mic),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Camera toggle
                            IconButton(
                                onClick = { manager.toggleVideoSending() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isVideoSendEnabled) Color.White.copy(alpha = 0.3f)
                                        else Color.Red.copy(alpha = 0.5f),
                                        RoundedCornerShape(22.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isVideoSendEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                    contentDescription = if (isVideoSendEnabled) stringResource(R.string.camera_cd_disable_camera) else stringResource(R.string.camera_cd_enable_camera),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Initialize the landscape controls overlay ComposeView.
 * Called from Java: CameraControlsKt.initLandscapeControls(composeView, onStop)
 *
 * @param composeView The ComposeView in the landscape layout
 * @param onStop Callback invoked when the stop button is pressed
 */
fun initLandscapeControls(composeView: ComposeView, onStop: Runnable) {
    composeView.apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val manager = remember { EspApplication.getViewportWebRtcManager() }
            LandscapeControlsOverlay(
                manager = manager,
                onStop = { onStop.run() }
            )
        }
    }
}

fun sendCameraCommand(coroutineScope: CoroutineScope,
                      nodeId: String,
                      apiManager: ApiManager,
                      command: String,
                      argList: List<Arg>,
                      onResponse: (CommandResponse) -> Unit) {
    val gson = Gson()
    val stringArgList = mutableListOf<String>()

    for (arg in argList) {
        if (!arg.optional && arg.value == null) return
        if (arg.value == null) break
        stringArgList.add(arg.value!!)
    }

    val requestBody = JsonObject()
    requestBody.addProperty(AppConstants.KEY_CMD, 4097)
    requestBody.addProperty(AppConstants.KEY_IS_BASE64, false)
    requestBody.addProperty(AppConstants.KEY_TIMEOUT, 1000)

    val nodeIds = JsonArray()
    nodeIds.add(nodeId)
    requestBody.add(AppConstants.KEY_NODE_IDS, nodeIds)

    val payloadJson = JsonObject()
    payloadJson.add("cmd", gson.toJsonTree(command))
    payloadJson.add("args", gson.toJsonTree(stringArgList))

    requestBody.add(AppConstants.KEY_DATA, payloadJson);

    Log.i(TAG, "sendCameraCommand: ${requestBody.toString()}")

    apiManager.sendCommandResponse(requestBody, object : ApiResponseListener {
        override fun onNetworkFailure(exception: Exception) {
            onResponse(CommandResponse.Failure("Network Failure while sending request"))
        }

        override fun onSuccess(data: Bundle?) {

            if (data!!.containsKey(AppConstants.KEY_REQUEST_ID)) {
                val reqId = data.getString(AppConstants.KEY_REQUEST_ID)
                coroutineScope.launch {
                    var polling = true
                    while (true) {
                        if (!polling) break

                        apiManager.getCommandResponseStatus(reqId, object : ApiResponseListener {
                            override fun onNetworkFailure(exception: Exception) {
                                onResponse(CommandResponse.Failure("Network Failure while receiving response"))
                                polling = false
                            }

                            override fun onResponseFailure(exception: Exception) {
                                onResponse(CommandResponse.Failure("Response Failure while receiving response"))
                                polling = false
                            }

                            override fun onSuccess(data: Bundle?) {
                                try {
                                    val status = data!!.getString(AppConstants.KEY_STATUS)

                                    // Display latest status
                                    Log.i(TAG, "onSuccess: resp status: $status")

                                    val gson = Gson()
                                    data class CombinedResponseClass_(
                                        val status: String,
                                        val description: String?,
                                        val listing: List<String?>?
                                    )

                                    // check if response received
                                    when (status) {
                                        "success" -> {
                                            val responseData =
                                                data.getString(AppConstants.KEY_RESPONSE_DATA)

                                            Log.i(TAG, "onSuccess: data: $responseData")


                                            try {
                                                val responseDataClass: CombinedResponseClass_ = gson.fromJson(responseData!!, CombinedResponseClass_::class.java)
                                                // check if parsed response has a status of success or failure
                                                when(responseDataClass.status)
                                                {
                                                    "success" -> {
                                                        if (responseDataClass.listing != null)
                                                        {
                                                            onResponse(CommandResponse.Success(CommandResponseData.LsData(responseDataClass.listing, command)))
                                                        }
                                                        else if (responseDataClass.description != null && responseDataClass.description != "")
                                                        {
                                                            onResponse(CommandResponse.Success(CommandResponseData.StringData(responseDataClass.description,command)))
                                                        }
                                                        else
                                                        {
                                                            onResponse(CommandResponse.Success(CommandResponseData.NoData(command)))
                                                        }
                                                    }
                                                    "failure" -> {
                                                        onResponse(CommandResponse.Failure(responseDataClass.description ?: "No response"))
                                                    }
                                                }
                                            }
                                            catch (err: JsonSyntaxException) {
                                                Log.e(TAG, "onSuccess Error: $err")
                                                onResponse(CommandResponse.Success(
                                                    CommandResponseData.StringData("onSuccess Error: $err", command)))
                                            }

                                            polling = false
                                        }
                                        "timed_out" -> {
                                            Log.i(TAG, "onSuccess: data: Reason: Timed Out")
                                            onResponse(CommandResponse.Failure("Timed out"))
                                            polling = false
                                        }
                                        "failure" -> {
                                            val statusDesc =
                                                data.getString(AppConstants.KEY_STATUS_DESCRIPTION)
                                            if (!TextUtils.isEmpty(statusDesc)) {
                                                onResponse(CommandResponse.Failure("Failed - Reason: $statusDesc"))
                                            } else {
                                                onResponse(CommandResponse.Failure("Failed - No response"))
                                            }
                                            polling = false
                                        }
                                        "in_progress" -> {
                                            onResponse(CommandResponse.InProgress)
                                        }
                                    }
                                } catch (e: java.lang.Exception) {
                                    Log.e(TAG, "Error parsing response", e)
                                    onResponse(CommandResponse.Failure("Error Parsing response, $e"))
                                }
                            }
                        })

                        delay(2000)
                    }
                }
            }
        }

        override fun onResponseFailure(exception: Exception) {
            onResponse(CommandResponse.Failure("Response Failure for request"))
        }
    })
}

@JvmOverloads
fun initCameraControls(view: View, nodeId: String, hasControlParam: Boolean = true): ComposeView {
    val composeView = view.findViewById<ComposeView>(R.id.camera_compose_view)
    composeView.apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val coroutineScope = rememberCoroutineScope()
            var responseData by remember { mutableStateOf<CommandResponse>(CommandResponse.NoCommandSent) }
            var isPlaying by remember { mutableStateOf(false) }
            var isLoading by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            var webRtcManager by remember { mutableStateOf<WebRtcViewportManager?>(null) }
            var surfaceViewRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

            var storedSessionInfo by remember { mutableStateOf<WebRtcChannelInfo?>(null) }
            var storedClientId by remember { mutableStateOf<String?>(null) }

            // Flag to prevent automatic cleanup when switching videos
            var isSwitchingVideo by remember { mutableStateOf(false) }

            // Deferred to wait for cleanup completion
            var cleanupComplete by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

            var videoSessionKey by remember { mutableIntStateOf(0) }

            // Recover existing manager after configuration change (e.g., rotation).
            // Skip stale managers (session already stopped via back-press) so the play
            // button shows instead of a frozen "playing" UI pointing at a dead session.
            LaunchedEffect(Unit) {
                EspApplication.getViewportWebRtcManager()?.let { existing ->
                    if (existing.isActive()) {
                        Log.d("CameraControls", "Rebinding existing WebRTC manager after config change")
                        webRtcManager = existing
                        EspApplication.setActiveWebRtcManager(existing)
                        isPlaying = true
                        isLoading = false
                    } else {
                        Log.d("CameraControls", "Stale viewport manager found (stopped), clearing")
                        EspApplication.clearViewportWebRtcManager()
                    }
                }
            }

            val channelName = "esp-v1-$nodeId"

            val onVideoPlay: () -> Unit = {
                // Check permissions first
                val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                if (!hasCameraPermission || !hasAudioPermission) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                            100
                        )
                    }
                    Toast.makeText(context, context.getString(R.string.camera_toast_permissions_required), Toast.LENGTH_SHORT).show()
                } else {
                    // Check if region is available (credentials fetched)
                    if (EspApplication.region.isNullOrEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, context.getString(R.string.camera_toast_fetching_credentials), Toast.LENGTH_SHORT).show()
                        }

                        // Wait for region to become available
                        coroutineScope.launch {
                            if (waitForCredentials()) {
                                Log.d("CameraControls", "Credentials available, starting video session")
                                isPlaying = true
                                isLoading = true
                                errorMessage = null
                            } else {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, context.getString(R.string.camera_toast_credentials_failed), Toast.LENGTH_LONG).show()
                                }
                                Log.e("CameraControls", "Timeout waiting for credentials")
                            }
                        }
                    } else {
                        Log.d("CameraControls", "Starting new video session")
                        isPlaying = true
                        isLoading = true
                        errorMessage = null
                    }
                }
            }

            val onVideoStop = {
                // Check if manager exists and is still active before attempting to stop
                val manager = webRtcManager
                if (manager == null || !manager.isActive()) {
                    Log.d("CameraControls", "onVideoStop: Manager is null or already stopped, just updating UI state")
                    isPlaying = false
                    isLoading = false
                    errorMessage = null
                    webRtcManager = null
                    EspApplication.clearActiveWebRtcManager()
                } else {
                    isSwitchingVideo = false  // Clear flag on explicit stop

                    // Show stream ended toast with duration before stopping
                    try {
                        val durationMs = manager.currentStats?.streamDurationMs ?: 0L
                        if (durationMs > 0) {
                            val seconds = durationMs / 1000
                            val minutes = seconds / 60
                            val hours = minutes / 60
                            val durationText = if (hours > 0) {
                                String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
                            } else {
                                String.format("%d:%02d", minutes, seconds % 60)
                            }
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, context.getString(R.string.camera_toast_stream_ended, durationText), Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("CameraControls", "Error getting stream duration: ${e.message}")
                    }

                    // Create deferred for cleanup
                    cleanupComplete = CompletableDeferred()
                    Log.d("CameraControls", "Created cleanup deferred, stopping manager")

                    try {
                        manager.stop()
                    } catch (e: Exception) {
                        Log.e("CameraControls", "Error stopping manager: ${e.message}", e)
                    }
                    webRtcManager = null
                    EspApplication.clearActiveWebRtcManager()
                    surfaceViewRenderer = null
                    isPlaying = false
                    isLoading = false
                    errorMessage = null
                }
            }

            val onFullscreen = {
                val manager = webRtcManager
                val renderer = surfaceViewRenderer

                if (manager != null && renderer != null) {
                    EspApplication.setViewportWebRtcManager(manager)
                    EspApplication.isLandscapeTransitionActive = true
                    manager.setViewportRenderer(renderer)

                    val channelInfo = manager.getChannelInfo()
                    if (channelInfo != null) {
                        EspApplication.region = channelInfo.region
                    }

                    val intent = Intent(context, WebRtcConfigActivity::class.java)
                    EspApplication.channelName = channelName
                    intent.putExtra("reuse_session", true)
                    context.startActivity(intent)
                }
            }

            // Preserve manager across configuration changes; cleanup handled by
            // EspDeviceActivity.onStop() via EspApplication.activeWebRtcManager.
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        val activity = context as? android.app.Activity
                        if (activity?.isChangingConfigurations == true) {
                            webRtcManager?.let { manager ->
                                Log.d("CameraControls", "Preserving WebRTC manager across config change")
                                EspApplication.setViewportWebRtcManager(manager)
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MaterialTheme(
                colorScheme =
                if (isSystemInDarkTheme())
                    darkColorScheme()
                else
                    lightColorScheme()) {
                CameraControlsWithViewport(
                    responseData = responseData,
                    onCommandSend = { command, argList ->
                        sendCameraCommand(
                            coroutineScope,
                            nodeId,
                            ApiManager.getInstance(context),
                            command,
                            argList
                        ) { data ->
                            when (data) {
                                is CommandResponse.InProgress -> Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, context.getString(R.string.camera_toast_command_in_progress, command), Toast.LENGTH_SHORT).show()
                                }
                                is CommandResponse.Failure -> Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, context.getString(R.string.camera_toast_command_failed, command, data.err), Toast.LENGTH_LONG).show()
                                }
                                is CommandResponse.Success -> {

                                    when(data.data) {
                                        is CommandResponseData.LsData, is CommandResponseData.StringData -> responseData = data
                                        is CommandResponseData.NoData -> Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(context, context.getString(R.string.camera_toast_command_success, data.data.command), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                is CommandResponse.NoCommandSent -> {}
                            }
                        }
                        if (command in listOf(AppConstants.CAMERA_COMMAND_VIEW_FROM,
                                AppConstants.CAMERA_COMMAND_VIEW_EVENT_FILES,
                                AppConstants.CAMERA_COMMAND_LIVE_MODE,
                                AppConstants.CAMERA_COMMAND_VIEW_FILES))
                        {
                            coroutineScope.launch {
                                // Stop existing video first to transition to stopped state
                                if (webRtcManager != null) {
                                    Log.d("CameraControls", "Stopping existing video before playing new one")
                                    onVideoStop()

                                    // Wait for cleanup to complete (signaled via onConnectionStateChanged callback)
                                    val deferred = cleanupComplete
                                    if (deferred != null) {
                                        Log.d("CameraControls", "Waiting for cleanup signal...")
                                        withTimeoutOrNull(2000) { deferred.await() }
                                    }
                                    Log.d("CameraControls", "Cleanup complete")
                                }
                                // Now start new video
                                Log.d("CameraControls", "Starting new video")
                                onVideoPlay()
                            }
                        }
                    },
                    nodeId = nodeId,
                    channelName = channelName,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onVideoPlay = onVideoPlay,
                    onVideoStop = onVideoStop,
                    onFullscreen = onFullscreen,
                    webRtcManager = webRtcManager,
                    surfaceViewRenderer = surfaceViewRenderer,
                    onWebRtcManagerChange = {
                        webRtcManager = it
                        if (it != null) {
                            isLoading = false
                            EspApplication.setActiveWebRtcManager(it)
                        } else {
                            EspApplication.clearActiveWebRtcManager()
                        }
                    },
                    onSurfaceViewRendererChange = { surfaceViewRenderer = it },
                    storedSessionInfo = storedSessionInfo,
                    storedClientId = storedClientId,
                    lifecycleOwner = lifecycleOwner,
                    isSwitchingVideo = isSwitchingVideo,
                    onSwitchingVideoChange = { isSwitchingVideo = it },
                    cleanupComplete = cleanupComplete,
                    onCleanupCompleteChange = { cleanupComplete = it },
                    showControls = hasControlParam,
                    videoSessionKey = videoSessionKey,
                    onSessionKeyIncrement = { videoSessionKey++ }
                )
            }
        }
    }

    return composeView
}

fun initViewportControls(composeView: ComposeView, channelName: String, nodeId: String) {
    composeView.apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            var isFullscreen by remember { mutableStateOf(false) }
            var isPlaying by remember { mutableStateOf(false) }
            var isLoading by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            var webRtcManager by remember { mutableStateOf<WebRtcViewportManager?>(null) }
            var surfaceViewRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
            var videoSessionKey by remember { mutableIntStateOf(0) }

            // Store session info for resuming after fullscreen (same client ID to reuse viewer slot)
            var storedSessionInfo by remember { mutableStateOf<WebRtcChannelInfo?>(null) }
            var storedClientId by remember { mutableStateOf<String?>(null) }

            // Flag to prevent automatic cleanup when switching videos
            var isSwitchingVideo by remember { mutableStateOf(false) }

            // Deferred to wait for cleanup completion
            var cleanupComplete by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

            // Recover existing manager after configuration change (e.g., rotation).
            // Skip stale managers (session already stopped via back-press) so the play
            // button shows instead of a frozen "playing" UI pointing at a dead session.
            LaunchedEffect(Unit) {
                EspApplication.getViewportWebRtcManager()?.let { existing ->
                    if (existing.isActive()) {
                        Log.d("CameraControls", "initViewportControls: Rebinding existing WebRTC manager after config change")
                        webRtcManager = existing
                        EspApplication.setActiveWebRtcManager(existing)
                        isPlaying = true
                        isLoading = false
                    } else {
                        Log.d("CameraControls", "initViewportControls: Stale viewport manager found (stopped), clearing")
                        EspApplication.clearViewportWebRtcManager()
                    }
                }
            }

            val onVideoPlay: () -> Unit = {
                // Check permissions first
                val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                if (!hasCameraPermission || !hasAudioPermission) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                            9393
                        )
                    }
                    Toast.makeText(context, context.getString(R.string.camera_toast_permissions_required_alt), Toast.LENGTH_SHORT).show()
                } else {
                    // Check if region is available (credentials fetched)
                    if (EspApplication.region.isNullOrEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, context.getString(R.string.camera_toast_fetching_credentials), Toast.LENGTH_SHORT).show()
                        }

                        // Wait for region to become available
                        coroutineScope.launch {
                            if (waitForCredentials()) {
                                Log.d("CameraControls", "initViewportControls: Credentials available, starting video session")
                                isPlaying = true
                                isLoading = true
                                errorMessage = null
                            } else {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, context.getString(R.string.camera_toast_credentials_failed), Toast.LENGTH_LONG).show()
                                }
                                Log.e("CameraControls", "initViewportControls: Timeout waiting for credentials")
                            }
                        }
                    } else {
                        // Start WebRTC connection - SurfaceViewRenderer will be created and WebRTC initialized in LaunchedEffect
                        Log.d("CameraControls", "initViewportControls: Starting new video session")
                        isPlaying = true
                        isLoading = true
                        errorMessage = null
                    }
                }
            }

            val onVideoStop = {
                // Check if manager exists and is still active before attempting to stop
                val manager = webRtcManager
                if (manager == null || !manager.isActive()) {
                    Log.d("CameraControls", "initViewportControls: onVideoStop: Manager is null or already stopped, just updating UI state")
                    isPlaying = false
                    isLoading = false
                    errorMessage = null
                    webRtcManager = null
                    EspApplication.clearViewportWebRtcManager()
                    EspApplication.clearActiveWebRtcManager()
                } else {
                    isSwitchingVideo = false  // Clear flag on explicit stop

                    // Show stream ended toast with duration before stopping
                    try {
                        val durationMs = manager.currentStats?.streamDurationMs ?: 0L
                        if (durationMs > 0) {
                            val seconds = durationMs / 1000
                            val minutes = seconds / 60
                            val hours = minutes / 60
                            val durationText = if (hours > 0) {
                                String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
                            } else {
                                String.format("%d:%02d", minutes, seconds % 60)
                            }
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, context.getString(R.string.camera_toast_stream_ended, durationText), Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("CameraControls", "initViewportControls: Error getting stream duration: ${e.message}")
                    }

                    // Prevent multiple simultaneous stop calls
                    if (isPlaying) {
                        Log.d("CameraControls", "initViewportControls: Stopping video - cleaning up WebRTC session")

                        // Create deferred for cleanup
                        cleanupComplete = CompletableDeferred()
                        Log.d("CameraControls", "initViewportControls: Created cleanup deferred, stopping manager")

                        // Stop WebRTC first (this will handle cleanup)
                        try {
                            manager.stop()
                        } catch (e: Exception) {
                            Log.e("CameraControls", "initViewportControls: Error stopping WebRTC: ${e.message}", e)
                        }

                        // Clear references and update state
                        webRtcManager = null
                        EspApplication.clearViewportWebRtcManager()
                        EspApplication.clearActiveWebRtcManager()
                        EspApplication.clearLandscapeSessionReferences()

                        // Don't set surfaceViewRenderer to null here - AndroidView will handle it
                        isPlaying = false
                        isLoading = false
                        errorMessage = null
                    }
                }
            }

            val onFullscreen = {
                // Reuse existing session if active - transfer video to landscape
                val manager = webRtcManager
                val renderer = surfaceViewRenderer

                if (isPlaying && manager != null && renderer != null) {
                    Log.d("CameraControls", "Reusing existing session - transferring to landscape")

                    // Store viewport renderer for transfer back
                    manager.setViewportRenderer(renderer)

                    // Store manager in EspApplication for landscape to access
                    EspApplication.setViewportWebRtcManager(manager)
                    EspApplication.isLandscapeTransitionActive = true

                    // Get channel info for passing to landscape
                    val channelInfo = manager.getChannelInfo()
                    if (channelInfo != null) {
                        EspApplication.region = channelInfo.region
                    }

                    // Launch landscape with reuse_session flag
                    val intent = Intent(context, WebRtcConfigActivity::class.java)
                    EspApplication.channelName = channelName
                    intent.putExtra("reuse_session", true)
                    context.startActivity(intent)

                    // Keep manager active - video will be displayed in landscape
                    // Don't change isPlaying - session is still active
                } else {
                    // No active session - create new one
                    Log.d("CameraControls", "No active session - creating new session in landscape")
                    EspApplication.isLandscapeTransitionActive = true
                    val intent = Intent(context, WebRtcConfigActivity::class.java)
                    EspApplication.channelName = channelName
                    context.startActivity(intent)
                }
            }

            // Cleanup when composable is disposed (e.g., back button pressed)
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        // Only cleanup on destroy if activity is finishing (not rotating)
                        val activity = context as? android.app.Activity
                        val isFinishing = activity?.isFinishing ?: false
                        val isChangingConfigurations = activity?.isChangingConfigurations ?: false

                        Log.d("CameraControls", "initViewportControls: Lifecycle ON_DESTROY - isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")

                        if (isFinishing && !isChangingConfigurations) {
                            Log.d("CameraControls", "initViewportControls: Activity finishing - cleaning up WebRTC session")
                            if (webRtcManager != null) {
                                onVideoStop()
                            }
                        } else if (!isFinishing && isChangingConfigurations) {
                            // Preserve manager across configuration change
                            webRtcManager?.let { manager ->
                                Log.d("CameraControls", "initViewportControls: Preserving WebRTC manager across config change")
                                EspApplication.setViewportWebRtcManager(manager)
                            }
                        } else {
                            Log.d("CameraControls", "initViewportControls: Configuration change (rotation) - keeping WebRTC session alive")
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    Log.d("CameraControls", "initViewportControls: Composable disposed - removing lifecycle observer")
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Keep screen on while video is playing
            DisposableEffect(isPlaying) {
                val activity = context as? Activity
                if (isPlaying) {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Initialize WebRTC when SurfaceViewRenderer is created and channel info is available
            LaunchedEffect(isPlaying, surfaceViewRenderer, webRtcManager) {
                Log.d("CameraControls", "LaunchedEffect triggered: isPlaying=$isPlaying, surfaceViewRenderer=${surfaceViewRenderer != null}, webRtcManager=${webRtcManager != null}")
                if (isPlaying && surfaceViewRenderer != null && webRtcManager == null) {
                    Log.d("CameraControls", "Starting new WebRTC session")

                    // Wait for credentials to be available if not already (skip if using stored session)
                    if (storedSessionInfo == null && EspApplication.region.isNullOrEmpty()) {
                        if (!waitForCredentials()) {
                            Log.e("CameraControls", "initViewportControls: Timeout waiting for credentials")
                            isLoading = false
                            isPlaying = false
                            errorMessage = context.getString(R.string.camera_toast_credentials_failed)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                            return@LaunchedEffect
                        }
                    }

                    // Clear any old video content from the renderer before starting new session
                    surfaceViewRenderer?.clearImage()
                    // Check if we have stored session info (from fullscreen transition)
                    val channelInfo: WebRtcChannelInfo = if (storedSessionInfo != null) {
                        Log.d("CameraControls", "Resuming with stored session info")
                        storedSessionInfo!!
                    } else {
                        // Fetch channel endpoints only; ICE servers will be fetched
                        // in parallel with WebSocket connect inside start()
                        val region = EspApplication.region ?: "us-east-1"
                        Log.d("CameraControls", "initViewportControls: Using region: $region for WebRTC connection")
                        val result = WebRtcChannelInfoHelper.fetchChannelEndpoints(region, channelName.trim(), ChannelRole.VIEWER)
                        result.getOrNull() ?: run {
                            result.onFailure { e ->
                                isLoading = false
                                isPlaying = false
                                errorMessage = context.getString(R.string.camera_toast_webrtc_start_failed, e.message ?: "")
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                            return@LaunchedEffect
                        }
                    }

                    val manager = WebRtcViewportManager(context, lifecycleOwner)
                    manager.setCallbacks(
                        onConnectionStateChanged = { connected ->
                            // Only process callbacks from the current active manager
                            if (manager == webRtcManager) {
                                Log.d("CameraControls", "Connection state changed: connected=$connected, isSwitchingVideo=$isSwitchingVideo")
                                isLoading = false
                                if (connected) {
                                    Log.d("CameraControls", "Connected successfully")
                                    isSwitchingVideo = false
                                    errorMessage = null
                                } else {
                                    // Signal cleanup is complete if switching
                                    cleanupComplete?.let {
                                        Log.d("CameraControls", "WebRTC cleanup complete - signaling deferred")
                                        it.complete(Unit)
                                        cleanupComplete = null
                                    }

                                    // Check if manager was explicitly stopped (not just transient disconnect)
                                    if (!manager.isActive()) {
                                        // Manager was stopped (e.g., from landscape mode)
                                        Log.d("CameraControls", "Manager was stopped - updating playing state")
                                        isPlaying = false
                                        webRtcManager = null
                                        surfaceViewRenderer = null
                                        videoSessionKey++
                                    } else {
                                        // Do NOT auto-stop on disconnect - ICE DISCONNECTED is often
                                        // transient on mobile networks and may recover.
                                        // Just log it; user can manually stop if needed.
                                        Log.d("CameraControls", "Connection lost - may recover, not auto-stopping")
                                    }
                                }
                            } else {
                                Log.d("CameraControls", "Ignoring callback from old manager: connected=$connected")
                            }
                        },
                        onError = { error ->
                            isLoading = false
                            errorMessage = error
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    // Set as active manager before starting to ensure callbacks are processed
                    webRtcManager = manager
                    EspApplication.setActiveWebRtcManager(manager)

                    try {
                        manager.start(
                            channelArn = channelInfo.channelArn,
                            streamArn = channelInfo.streamArn,
                            wssEndpoint = channelInfo.wssEndpoint,
                            webrtcEndpoint = channelInfo.webrtcEndpoint,
                            region = channelInfo.region,
                            iceServers = channelInfo.iceServers,
                            dataEndpoint = channelInfo.dataEndpoint,
                            surfaceViewRenderer = surfaceViewRenderer!!,
                            isMaster = false,
                            clientId = storedClientId  // Reuse client ID if available
                        )
                        storedSessionInfo = null
                        storedClientId = null
                    } catch (e: Exception) {
                        Log.e("CameraControls", "initViewportControls: Failed to start WebRTC: ${e.message}", e)
                        isLoading = false
                        isPlaying = false
                        errorMessage = context.getString(R.string.camera_toast_start_failed, e.message ?: "")
                        webRtcManager = null
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            // Auto-resume when returning from fullscreen if we have stored session info
            LaunchedEffect(storedSessionInfo, surfaceViewRenderer) {
                // Check if we should auto-resume (when coming back from fullscreen)
                if (!isPlaying && storedSessionInfo != null && surfaceViewRenderer != null && webRtcManager == null) {
                    Log.d("CameraControls", "Auto-resuming video after returning from fullscreen")
                    isPlaying = true
                    isLoading = true
                }
            }

            // Transfer landscape session back to viewport when returning from fullscreen
            LaunchedEffect(surfaceViewRenderer) {
                if (surfaceViewRenderer == null) return@LaunchedEffect

                val landscapeVideoTrack = EspApplication.landscapeVideoTrack
                val landscapeEglBase = EspApplication.landscapeEglBase

                if (landscapeVideoTrack == null || landscapeEglBase == null) return@LaunchedEffect

                val renderer = surfaceViewRenderer!!
                renderer.post {
                    renderer.postDelayed({
                        try {
                            try {
                                renderer.init(landscapeEglBase.eglBaseContext, null)
                                renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                renderer.setMirror(false)
                            } catch (e: IllegalStateException) {
                                // Pickup failed — dispose the orphaned natives rather than just
                                // null the references (otherwise PeerConnection/EglBase/Track leak).
                                EspApplication.clearLandscapeSession()
                                return@postDelayed
                            }

                            try {
                                val trackState = landscapeVideoTrack.state()
                                if (trackState == org.webrtc.MediaStreamTrack.State.ENDED) {
                                    EspApplication.clearLandscapeSession()
                                    return@postDelayed
                                }
                            } catch (e: Exception) {
                            }

                            try {
                                landscapeVideoTrack.addSink(renderer)
                                isPlaying = true
                                isLoading = false
                            } catch (e: Exception) {
                                EspApplication.clearLandscapeSession()
                                return@postDelayed
                            }

                            EspApplication.landscapeVideoTrack = null
                            EspApplication.landscapeEglBase = null
                            EspApplication.landscapeRenderer = null
                            EspApplication.landscapePeerConnection = null
                        } catch (e: Exception) {
                            EspApplication.clearLandscapeSession()
                        }
                    }, 300)
                }
            }

            // Cleanup on dispose
            DisposableEffect(Unit) {
                onDispose {
                    Log.d("CameraControls", "Disposing CameraControls - cleaning up WebRTC resources")

                    val activity = context as? android.app.Activity
                    val isChangingConfigurations = activity?.isChangingConfigurations ?: false

                    if (!isChangingConfigurations) {
                        // Activity is finishing or going to background — stop the session
                        Log.d("CameraControls", "Stopping WebRTC session on dispose")
                        try {
                            webRtcManager?.stop()
                        } catch (e: Exception) {
                            Log.e("CameraControls", "Error stopping WebRTC on dispose: ${e.message}", e)
                        }
                        // Clear viewport manager reference if it was set
                        EspApplication.clearViewportWebRtcManager()
                    } else {
                        // Configuration change (rotation) - preserve manager
                        Log.d("CameraControls", "Config change - preserving WebRTC manager")
                    }

                    if (EspApplication.landscapeVideoTrack != null || EspApplication.landscapeEglBase != null ||
                        EspApplication.landscapePeerConnection != null) {
                        // Dispose the unconsumed landscape session (track/EglBase/PeerConnection)
                        // rather than just nulling references — otherwise these native resources
                        // leak for the life of the process.
                        EspApplication.clearLandscapeSession()
                    }

                    surfaceViewRenderer?.let { view ->
                        try {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    view.release()
                                } catch (e: Exception) {
                                    Log.e("CameraControls", "Error releasing view on dispose: ${e.message}", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("CameraControls", "Error posting view release: ${e.message}", e)
                        }
                    }
                }
            }

            MaterialTheme {
                WebRtcVideoPlayer(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    webRtcManager = webRtcManager,
                    onVideoPlay = onVideoPlay,
                    onVideoStop = onVideoStop,
                    onFullscreen = onFullscreen,
                    onSurfaceViewRendererChange = { surfaceViewRenderer = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 2f)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(8.dp)
                        ),
                    videoSessionKey = videoSessionKey
                )
            }
        }
    }
}

enum class ArgType {
    StringArg,
    TimeArg,
}
data class Arg (
    val name: String,
    val optional: Boolean,
    var value: String? = null,
    val type: ArgType = ArgType.StringArg,
)

data class Command (
    val name: String,
    val argList: List<Arg> = listOf(),
)

sealed class CommandResponseData(val command: String) {
    data class NoData(val command_: String): CommandResponseData(command_)
    data class LsData(val items: List<String?>, val command_: String): CommandResponseData(command_)
    data class StringData(val data: String, val command_: String): CommandResponseData(command_)
}

sealed class CommandResponse {
    object NoCommandSent: CommandResponse()
    object InProgress: CommandResponse()
    data class Failure(val err: String): CommandResponse()
    data class Success(val data: CommandResponseData): CommandResponse()
}

@Composable
fun ListingTile(name: String,
                onClickPlay: () -> Unit,
                onClickDelete: (() -> Unit)? = null,
                onClickList: (() -> Unit)? = null) {
    Row (modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            colors = ButtonDefaults.textButtonColors().copy(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onBackground),
            onClick = onClickPlay) {
            Text(name)
        }
        Row {
            if (onClickList != null)
                IconButton(onClick = onClickList) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
            if (onClickDelete != null)
                IconButton(onClick = onClickDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
            IconButton(onClick = onClickPlay) { Icon(Icons.Default.PlayArrow, contentDescription = null) }
        }
    }
}

@Composable
fun ResponseDisplay(
    responseData: CommandResponse,
    onCommandSend: (command: String, argList: List<Arg>) -> Unit,
    showNoCommandPrompt: Boolean = true
) {
    val filenameRegex = """(\d+).mkv""".toRegex()
    when (responseData) {
        is CommandResponse.Failure -> Text(stringResource(R.string.camera_cmd_failed, responseData.err))
        is CommandResponse.Success -> {
            when (responseData.data) {
                is CommandResponseData.NoData -> Text(stringResource(R.string.camera_cmd_no_data, responseData.data.command))
                is CommandResponseData.LsData -> {
                    if (responseData.data.command in listOf(
                        AppConstants.CAMERA_COMMAND_LIST_FILES,
                        AppConstants.CAMERA_COMMAND_FAST_LIST_FILES,
                        AppConstants.CAMERA_COMMAND_LIST_EVENT_FILES))
                    {
                        data class FileWithEpoch(val filename: String, val epoch: Long)

                        responseData.data.items
                            .mapNotNull { filename ->
                                if(filename == null)
                                    return@mapNotNull null
                                val match = filenameRegex.matchEntire(filename)
                                if (match == null)
                                    return@mapNotNull null

                                val epoch = match.groupValues[1].toLong()
                                val ret = FileWithEpoch(filename, epoch)

                                ret
                            }
                            .sortedByDescending { file -> file.epoch }
                            .map { fileWithEpoch ->
                                val epoch = fileWithEpoch.epoch
                                val filename = fileWithEpoch.filename

                                val instant = Instant.ofEpochSecond(epoch)
                                val formatter = DateTimeFormatter
                                    .ofPattern("dd/MM/yy, HH:mm:ss")
                                    .withZone(ZoneId.systemDefault())


                                val formattedDate = formatter.format(instant)
                                val onClickPlay = {
                                    onCommandSend(
                                        AppConstants.CAMERA_COMMAND_VIEW_FILES,
                                        listOf(Arg(filename, false, filename))
                                    )
                                }

                                val onClickDelete = {
                                    onCommandSend(
                                        AppConstants.CAMERA_COMMAND_DELETE_FILE,
                                        listOf(Arg(filename, false, filename))
                                    )
                                }

                                ListingTile(formattedDate,
                                    onClickDelete = onClickDelete,
                                    onClickPlay = onClickPlay,
                                )
                            }
                    }
                    else if (responseData.data.command in listOf(AppConstants.CAMERA_COMMAND_LIST_EVENTS))
                    {
                        responseData.data.items.forEach { filename ->
                            if (filename == null) return@forEach

                            val onClickPlay = {
                                onCommandSend(
                                    AppConstants.CAMERA_COMMAND_VIEW_EVENT_FILES,
                                    listOf(Arg(filename, false, filename))
                                )
                            }

                            val onClickList = {
                                onCommandSend(
                                    AppConstants.CAMERA_COMMAND_LIST_EVENT_FILES,
                                    listOf(Arg(filename, false, filename))
                                )
                            }

                            ListingTile(filename,
                                onClickList = onClickList,
                                onClickPlay = onClickPlay,
                            )
                        }
                    }
                    else
                    {
                        Text(stringResource(R.string.camera_cmd_unknown_listing))
                    }
                }

                is CommandResponseData.StringData -> Text(responseData.data.data)
            }
        }

        is CommandResponse.NoCommandSent -> {
            if (showNoCommandPrompt) {
                Text(stringResource(R.string.camera_cmd_send_a_command))
            }
        }
        is CommandResponse.InProgress -> Text(stringResource(R.string.camera_cmd_in_progress))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraControlsWithViewport(
    responseData: CommandResponse,
    onCommandSend: (String, List<Arg>) -> Unit,
    nodeId: String,
    channelName: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onVideoPlay: () -> Unit,
    onVideoStop: () -> Unit,
    onFullscreen: () -> Unit,
    webRtcManager: WebRtcViewportManager?,
    surfaceViewRenderer: SurfaceViewRenderer?,
    onWebRtcManagerChange: (WebRtcViewportManager?) -> Unit,
    onSurfaceViewRendererChange: (SurfaceViewRenderer?) -> Unit,
    storedSessionInfo: WebRtcChannelInfo?,
    storedClientId: String?,
    lifecycleOwner: LifecycleOwner,
    isSwitchingVideo: Boolean,
    onSwitchingVideoChange: (Boolean) -> Unit,
    cleanupComplete: CompletableDeferred<Unit>?,
    onCleanupCompleteChange: (CompletableDeferred<Unit>?) -> Unit,
    showControls: Boolean = true,
    videoSessionKey: Int = 0,
    onSessionKeyIncrement: () -> Unit = {}
) {
    // Flag to toggle between simple UI (3 buttons) and advanced UI (command dropdown)
    val useSimpleUI = true  // Set to false to use the old advanced command UI

    var expanded by remember { mutableStateOf(false) }
    var selectedCommand by remember { mutableStateOf<Int?>(null) }
    val currentArgList = remember { mutableStateListOf<Arg>() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Keep screen on while video is playing
    DisposableEffect(isPlaying) {
        val activity = context as? Activity
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Initialize WebRTC when SurfaceViewRenderer is created and channel info is available
    LaunchedEffect(isPlaying, surfaceViewRenderer, webRtcManager) {
        Log.d("CameraControls", "CameraControlsWithViewport LaunchedEffect: isPlaying=$isPlaying, surfaceViewRenderer=${surfaceViewRenderer != null}, webRtcManager=${webRtcManager != null}")
        if (isPlaying && surfaceViewRenderer != null && webRtcManager == null) {
            Log.d("CameraControls", "CameraControlsWithViewport starting new WebRTC session")

            // Wait for credentials to be available if not already
            if (EspApplication.region.isNullOrEmpty()) {
                if (!waitForCredentials()) {
                    Log.e("CameraControls", "Timeout waiting for credentials")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, context.getString(R.string.camera_toast_credentials_failed), Toast.LENGTH_LONG).show()
                    }
                    // Stop the video playback attempt
                    onVideoStop()
                    return@LaunchedEffect
                }
            }

            // Clear any old video content from the renderer before starting new session
            surfaceViewRenderer?.clearImage()
            val region = EspApplication.region ?: "us-east-1"
            Log.d("CameraControls", "Using region: $region for WebRTC connection")
            val result = WebRtcChannelInfoHelper.fetchChannelEndpoints(region, channelName.trim(), ChannelRole.VIEWER)
            val channelInfo = result.getOrNull() ?: run {
                result.onFailure { e ->
                    Toast.makeText(context, context.getString(R.string.camera_toast_webrtc_start_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
                return@LaunchedEffect
            }

            val manager = WebRtcViewportManager(context, lifecycleOwner)
            manager.setCallbacks(
                onConnectionStateChanged = { connected ->
                    // Only process callbacks from the current active manager
                    if (manager == webRtcManager || webRtcManager == null) {
                        Log.d("CameraControls", "CameraControlsWithViewport connection state: connected=$connected, isSwitchingVideo=$isSwitchingVideo")
                        if (connected) {
                            // Successfully connected, clear switching flag
                            Log.d("CameraControls", "CameraControlsWithViewport connected successfully")
                            onSwitchingVideoChange(false)
                        } else {
                            // Signal cleanup is complete if switching
                            cleanupComplete?.let {
                                Log.d("CameraControls", "CameraControlsWithViewport cleanup complete - signaling deferred")
                                it.complete(Unit)
                                onCleanupCompleteChange(null)
                            }

                            // Check if manager was explicitly stopped (e.g. from landscape mode)
                            if (!manager.isActive()) {
                                Log.d("CameraControls", "CameraControlsWithViewport manager was stopped - updating UI state")
                                onVideoStop()
                                onSurfaceViewRendererChange(null)
                                onSessionKeyIncrement()
                            } else {
                                // Do NOT auto-stop on disconnect - ICE DISCONNECTED is often
                                // transient on mobile networks and may recover.
                                Log.d("CameraControls", "CameraControlsWithViewport connection lost - may recover, not auto-stopping")
                            }
                        }
                    } else {
                        Log.d("CameraControls", "CameraControlsWithViewport ignoring callback from old manager: connected=$connected")
                    }
                },
                onError = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            )

            try {
                manager.start(
                    channelArn = channelInfo.channelArn,
                    streamArn = channelInfo.streamArn,
                    wssEndpoint = channelInfo.wssEndpoint,
                    webrtcEndpoint = channelInfo.webrtcEndpoint,
                    region = channelInfo.region,
                    iceServers = channelInfo.iceServers,
                    dataEndpoint = channelInfo.dataEndpoint,
                    surfaceViewRenderer = surfaceViewRenderer!!,
                    isMaster = false,
                    clientId = storedClientId
                )
                onWebRtcManagerChange(manager)
                EspApplication.setViewportWebRtcManager(manager)
            } catch (e: Exception) {
                Log.e("CameraControls", "CameraControlsWithViewport: Failed to start WebRTC: ${e.message}", e)
                onWebRtcManagerChange(null)
                onVideoStop()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, context.getString(R.string.camera_toast_start_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Transfer landscape session back to viewport when returning from fullscreen
    LaunchedEffect(surfaceViewRenderer) {
        if (surfaceViewRenderer == null) return@LaunchedEffect

        val landscapeVideoTrack = EspApplication.landscapeVideoTrack
        val landscapeEglBase = EspApplication.landscapeEglBase

        if (landscapeVideoTrack == null || landscapeEglBase == null) return@LaunchedEffect

        val renderer = surfaceViewRenderer!!
        renderer.post {
            renderer.postDelayed({
                try {
                    try {
                        renderer.init(landscapeEglBase.eglBaseContext, null)
                        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        renderer.setMirror(false)
                    } catch (e: IllegalStateException) {
                        EspApplication.clearLandscapeSession()
                        return@postDelayed
                    }

                    try {
                        val trackState = landscapeVideoTrack.state()
                        if (trackState == org.webrtc.MediaStreamTrack.State.ENDED) {
                            EspApplication.clearLandscapeSession()
                            return@postDelayed
                        }
                    } catch (e: Exception) {
                    }

                    try {
                        landscapeVideoTrack.addSink(renderer)
                        onVideoPlay()
                    } catch (e: Exception) {
                        EspApplication.clearLandscapeSession()
                        return@postDelayed
                    }

                    EspApplication.landscapeVideoTrack = null
                    EspApplication.landscapeEglBase = null
                    EspApplication.landscapeRenderer = null
                    EspApplication.landscapePeerConnection = null
                } catch (e: Exception) {
                    EspApplication.clearLandscapeSession()
                }
            }, 300)
        }
    }

    val commands = listOf(
        Command(AppConstants.CAMERA_COMMAND_LIST_FILES),
        Command(AppConstants.CAMERA_COMMAND_FAST_LIST_FILES),
        Command(AppConstants.CAMERA_COMMAND_LIST_EVENTS),
        Command(AppConstants.CAMERA_COMMAND_LIST_EVENT_FILES, listOf(
            Arg(AppConstants.CAMERA_COMMAND_ARG_EVENT, optional = false))),
        Command(AppConstants.CAMERA_COMMAND_VIEW_FROM, listOf(
            Arg(AppConstants.CAMERA_COMMAND_ARG_START_TIME, optional = false, type = ArgType.TimeArg),
            Arg(AppConstants.CAMERA_COMMAND_ARG_END_TIME, optional = true, type = ArgType.TimeArg))),
        Command(AppConstants.CAMERA_COMMAND_PUT_MEDIA_START),
        Command(AppConstants.CAMERA_COMMAND_PUT_MEDIA_STOP),
        Command(AppConstants.CAMERA_COMMAND_SAVE_CLIP),
        Command(AppConstants.CAMERA_COMMAND_FORMAT_SD_CARD),
    )
    var skip by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(20) }

    val showStartTimeDialogState = remember { mutableStateOf(false) }
    val showEndTimeDialogState = remember { mutableStateOf(false) }

    val startTimePickerState = rememberTimePickerState()
    val endTimePickerState = rememberTimePickerState()

    if (showControls) {
        Card {
            Column(Modifier.padding(10.dp)) {
                WebRtcVideoPlayer(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    webRtcManager = webRtcManager,
                    onVideoPlay = onVideoPlay,
                    onVideoStop = onVideoStop,
                    onFullscreen = onFullscreen,
                    onSurfaceViewRendererChange = onSurfaceViewRendererChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(8.dp)
                        ),
                    videoSessionKey = videoSessionKey
                )

            if (useSimpleUI) {
                // Simple UI: Three direct action buttons
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onCommandSend(AppConstants.CAMERA_COMMAND_LIVE_MODE, emptyList())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            stringResource(R.string.camera_cmd_live_view),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Button(
                        onClick = {
                            onCommandSend(AppConstants.CAMERA_COMMAND_LIST_FILES, emptyList())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            stringResource(R.string.camera_cmd_file_list),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Button(
                        onClick = {
                            onCommandSend(AppConstants.CAMERA_COMMAND_SAVE_CLIP, emptyList())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            stringResource(R.string.camera_cmd_save_clip),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                // Advanced UI: Command dropdown with Send button (old UI)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box {
                        TextButton(colors = ButtonDefaults.textButtonColors().copy(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            onClick = { expanded = !expanded }) {
                            Text(if (selectedCommand == null) stringResource(R.string.camera_cmd_command) else commands[selectedCommand!!].name)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            commands.forEachIndexed { index, command ->
                                DropdownMenuItem(
                                    text = { Text(command.name) },
                                    onClick = {
                                        currentArgList.clear()
                                        currentArgList.addAll(commands[index].argList)
                                        expanded = false
                                        selectedCommand = index
                                    }
                                )
                            }
                        }
                    }
                    TextButton(
                        enabled = responseData != CommandResponse.InProgress,
                        colors = ButtonDefaults.textButtonColors().copy(containerColor = MaterialTheme.colorScheme.onPrimaryContainer, contentColor = MaterialTheme.colorScheme.background),
                            onClick = {
                        if (selectedCommand == null) return@TextButton
                        onCommandSend(commands[selectedCommand!!].name, currentArgList)
                    }) {
                        Text(stringResource(R.string.camera_cmd_send))
                    }
                }
            }

            if (!useSimpleUI) {
                // Arguments UI - only show for advanced command UI
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    currentArgList.forEachIndexed { index, arg ->
                    when(arg.type) {
                        ArgType.StringArg ->
                            TextField(
                                modifier = Modifier.weight(1f, fill = false),
                                value = arg.value ?: "",
                                label = { Text(arg.name) },
                                onValueChange = { newValue: String ->
                                    if (arg.name == AppConstants.CAMERA_COMMAND_ARG_SKIP)
                                        skip = arg.value?.toIntOrNull() ?: 0
                                    else if (arg.name == AppConstants.CAMERA_COMMAND_ARG_LIMIT)
                                        limit = arg.value?.toIntOrNull() ?: 20
                                    val newArg = arg.copy(value = newValue)
                                    currentArgList[index] = newArg
                                },
                                maxLines = 1,
                            )

                        ArgType.TimeArg -> {
                            var showDialog by if (arg.name == AppConstants.CAMERA_COMMAND_ARG_START_TIME) showStartTimeDialogState else showEndTimeDialogState
                            val timePickerState = if (arg.name == AppConstants.CAMERA_COMMAND_ARG_START_TIME) startTimePickerState else endTimePickerState
                            if (showDialog) {
                                TimePickerDialog(
                                    onDismissRequest = { showDialog = false },
                                    title = { Text(arg.name) },
                                    confirmButton = { Button(onClick = {
                                        val now = LocalDate.now()
                                        val todayStart = now.atStartOfDay(ZoneId.systemDefault())
                                        val todayStartEpoch = todayStart.toEpochSecond()   // ms

                                        val epochWithTime =
                                            todayStartEpoch +
                                                    timePickerState.hour * 60 * 60 +
                                                    timePickerState.minute * 60

                                        arg.value = epochWithTime.toString()

                                        showDialog = false
                                    }) { Text(stringResource(R.string.camera_cmd_confirm)) } })
                                {
                                    TimePicker(state = timePickerState)
                                }
                            }
                            Button(onClick = {
                                val now = Instant.now()
                                val zoneTime = now.atZone(ZoneId.systemDefault())

                                timePickerState.minute = zoneTime.minute
                                timePickerState.hour = zoneTime.hour

                                showDialog = true
                            }) { Text(
                                if (arg.value.isNullOrEmpty()) arg.name
                                else arg.value?:arg.name)}
                        }
                    }
                }
            }
            }  // End of if (!useSimpleUI) for arguments

            ResponseDisplay(
                responseData = responseData,
                onCommandSend = onCommandSend,
                showNoCommandPrompt = !useSimpleUI
            )

            if (!useSimpleUI && selectedCommand != null && commands[selectedCommand!!].name in listOf(AppConstants.CAMERA_COMMAND_LIST_FILES, AppConstants.CAMERA_COMMAND_FAST_LIST_FILES)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(
                            enabled = skip > 0,
                        onClick = {
                            skip = max(skip - limit, 0)
                            onCommandSend(commands[selectedCommand!!].name, listOf(
                                Arg(AppConstants.CAMERA_COMMAND_ARG_SKIP, optional = true, value = skip.toString()),
                                Arg(AppConstants.CAMERA_COMMAND_ARG_LIMIT, optional = true, value = limit.toString()),
                            ))
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                        IconButton(onClick = {
                            skip = skip + limit
                            onCommandSend(commands[selectedCommand!!].name, listOf(
                                Arg(AppConstants.CAMERA_COMMAND_ARG_SKIP, optional = true, value = skip.toString()),
                                Arg(AppConstants.CAMERA_COMMAND_ARG_LIMIT, optional = true, value = limit.toString()),
                            ))
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                }
            }
        }
    } else {
        // Viewport only mode (no controls) - same unified player
        WebRtcVideoPlayer(
            isPlaying = isPlaying,
            isLoading = isLoading,
            errorMessage = errorMessage,
            webRtcManager = webRtcManager,
            onVideoPlay = onVideoPlay,
            onVideoStop = onVideoStop,
            onFullscreen = onFullscreen,
            onSurfaceViewRendererChange = onSurfaceViewRendererChange,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(8.dp)
                ),
            videoSessionKey = videoSessionKey
        )
    }
}
