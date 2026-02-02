/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// StreamScreen - DAT Camera Streaming UI with LiveKit Integration
//
// This composable demonstrates the main streaming UI for DAT camera functionality. It shows how to
// display live video from wearable devices, handle photo capture, and stream to LiveKit WebRTC.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.livekit.LiveKitConnectionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import io.livekit.android.room.track.RemoteVideoTrack
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.SurfaceViewRenderer

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
            StreamViewModel.Factory(
                application = (LocalActivity.current as ComponentActivity).application,
                wearablesViewModel = wearablesViewModel,
            ),
        ),
) {
    val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()
    val liveKitState by streamViewModel.liveKitState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { streamViewModel.startStream() }

    Box(modifier = modifier.fillMaxSize()) {
        // If we have remote video, show it full screen, otherwise show local video
        if (liveKitState.remoteVideoTrack != null) {
            // Remote video from other participants (full screen)
            RemoteVideoView(
                track = liveKitState.remoteVideoTrack!!,
                modifier = Modifier.fillMaxSize()
            )

            // Local video from glasses (picture-in-picture in top-right corner)
            streamUiState.videoFrame?.let { videoFrame ->
                Image(
                    bitmap = videoFrame.asImageBitmap(),
                    contentDescription = "Local video",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(120.dp, 90.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            // No remote video, show local video full screen
            streamUiState.videoFrame?.let { videoFrame ->
                Image(
                    bitmap = videoFrame.asImageBitmap(),
                    contentDescription = stringResource(R.string.live_stream),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // LiveKit status bar at top
        LiveKitStatusBar(
            liveKitState = liveKitState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Bottom controls
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(all = 24.dp)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LiveKit controls row
                LiveKitControlsRow(
                    liveKitState = liveKitState,
                    onConnectClick = {
                        if (liveKitState.isConnected) {
                            streamViewModel.disconnectFromLiveKit()
                        } else {
                            // FIXME: Replace with your server URL and token
                            streamViewModel.connectToLiveKit(
                                "wss://gemini-live-test-qrwqjmf5.livekit.cloud",
                                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3Njk5MDA4NjAsImlkZW50aXR5IjoicmF5YmFuLWdsYXNzZXMiLCJpc3MiOiJBUEl6R1FFMnJDaGNzUXAiLCJuYW1lIjoicmF5YmFuLWdsYXNzZXMiLCJuYmYiOjE3Njk4MTQ0NjAsInN1YiI6InJheWJhbi1nbGFzc2VzIiwidmlkZW8iOnsicm9vbSI6InF1aWNrc3RhcnQgcm9vbSIsInJvb21Kb2luIjp0cnVlfX0.YqrYgp73kAzUUI3PalE0PYxSp7c7Xm22NXBZNqJQenE"
                            )
                        }
                    },
                    onGlassesVideoClick = {
                        if (liveKitState.isPublishingVideo) {
                            streamViewModel.stopPublishingVideo()
                        } else {
                            streamViewModel.startPublishingVideo()
                        }
                    },
                    onAudioClick = {
                        if (liveKitState.isPublishingAudio) {
                            streamViewModel.stopPublishingAudio()
                        } else {
                            streamViewModel.startPublishingAudio()
                        }
                    },
                    onMuteClick = { streamViewModel.toggleAudioMute() }
                )

                // Original controls row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwitchButton(
                        label = stringResource(R.string.stop_stream_button_title),
                        onClick = {
                            streamViewModel.disconnectFromLiveKit()
                            streamViewModel.stopStream()
                            wearablesViewModel.navigateToDeviceSelection()
                        },
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                    )

                    // Timer button
                    TimerButton(
                        timerMode = streamUiState.timerMode,
                        onClick = { streamViewModel.cycleTimerMode() },
                    )
                    // Photo capture button
                    CaptureButton(
                        onClick = { streamViewModel.capturePhoto() },
                    )
                }
            }
        }

        // Countdown timer display
        streamUiState.remainingTimeSeconds?.let { seconds ->
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            Text(
                text = stringResource(id = R.string.time_remaining, minutes, remainingSeconds),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 140.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    // Share photo dialog
    streamUiState.capturedPhoto?.let { photo ->
        if (streamUiState.isShareDialogVisible) {
            SharePhotoDialog(
                photo = photo,
                onDismiss = { streamViewModel.hideShareDialog() },
                onShare = { bitmap ->
                    streamViewModel.sharePhoto(bitmap)
                    streamViewModel.hideShareDialog()
                },
            )
        }
    }
}

@Composable
private fun LiveKitStatusBar(
    liveKitState: com.meta.wearable.dat.externalsampleapps.cameraaccess.livekit.LiveKitUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connection status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (liveKitState.connectionState) {
                            LiveKitConnectionState.CONNECTED -> AppColor.Green
                            LiveKitConnectionState.CONNECTING,
                            LiveKitConnectionState.RECONNECTING -> AppColor.Yellow
                            LiveKitConnectionState.ERROR -> AppColor.Red
                            LiveKitConnectionState.DISCONNECTED -> Color.Gray
                        }
                    )
            )

            Column {
                Text(
                    text = "LiveKit",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = liveKitState.statusText,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }

        // Publishing status icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (liveKitState.isPublishingVideo) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Glasses video",
                    tint = AppColor.Green,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (liveKitState.isPublishingAudio) {
                Icon(
                    imageVector = if (liveKitState.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Audio publishing",
                    tint = if (liveKitState.isAudioMuted) AppColor.Yellow else AppColor.Green,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (liveKitState.hasRemoteAudioTrack) {
                Text(
                    text = "🔊",
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun LiveKitControlsRow(
    liveKitState: com.meta.wearable.dat.externalsampleapps.cameraaccess.livekit.LiveKitUiState,
    onConnectClick: () -> Unit,
    onGlassesVideoClick: () -> Unit,
    onAudioClick: () -> Unit,
    onMuteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // First row: Connect button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connect/Disconnect button
            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (liveKitState.isConnected) AppColor.Red else AppColor.DeepBlue
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (liveKitState.isConnected) Icons.Default.WifiOff else Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (liveKitState.isConnected) "Disconnect" else "Connect AI",
                    fontSize = 12.sp
                )
            }
        }

        // Second row: Video and Audio sources
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glasses video button with label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onGlassesVideoClick,
                    enabled = liveKitState.isConnected,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (liveKitState.isPublishingVideo) AppColor.Green.copy(alpha = 0.8f)
                            else Color.Gray.copy(alpha = 0.5f)
                        )
                ) {
                    Icon(
                        imageVector = if (liveKitState.isPublishingVideo) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Glasses",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Glasses",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = if (liveKitState.isPublishingVideo) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Audio publish button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onAudioClick,
                    enabled = liveKitState.isConnected,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (liveKitState.isPublishingAudio) AppColor.Green.copy(alpha = 0.8f)
                            else Color.Gray.copy(alpha = 0.5f)
                        )
                ) {
                    Icon(
                        imageVector = if (liveKitState.isPublishingAudio) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Toggle audio",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Audio",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }

            // Mute button (only when audio is publishing)
            if (liveKitState.isPublishingAudio) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onMuteClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (liveKitState.isAudioMuted) AppColor.Yellow.copy(alpha = 0.8f)
                                else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = if (liveKitState.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Toggle mute",
                            tint = if (liveKitState.isAudioMuted) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (liveKitState.isAudioMuted) "Muted" else "Active",
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

/**
 * Composable for rendering remote video track from LiveKit
 */
@Composable
fun RemoteVideoView(
    track: RemoteVideoTrack,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val eglBase = remember { EglBase.create() }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBase.eglBaseContext, null)
                setMirror(false)
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            }
        },
        modifier = modifier,
        update = { view ->
            // Remove any existing renderer first
            try {
                track.removeRenderer(view)
            } catch (e: Exception) {
                // Ignore if not already attached
            }
            // Add renderer
            track.addRenderer(view)
        },
        onRelease = { view ->
            try {
                track.removeRenderer(view)
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
            view.release()
        }
    )

    DisposableEffect(track) {
        onDispose {
            eglBase.release()
        }
    }
}
