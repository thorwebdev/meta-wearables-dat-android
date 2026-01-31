/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.livekit

import io.livekit.android.room.track.RemoteVideoTrack

/**
 * UI State for LiveKit connection and streaming
 */
data class LiveKitUiState(
    val connectionState: LiveKitConnectionState = LiveKitConnectionState.DISCONNECTED,
    val roomName: String = "quickstart room",
    val participantName: String = "rayban-glasses",
    val isPublishingVideo: Boolean = false,
    val isPublishingAudio: Boolean = false,
    val isAudioMuted: Boolean = false,
    val hasRemoteAudioTrack: Boolean = false,
    val remoteVideoTrack: RemoteVideoTrack? = null,
    val errorMessage: String? = null,
) {
    val isConnected: Boolean
        get() = connectionState == LiveKitConnectionState.CONNECTED

    val canPublish: Boolean
        get() = isConnected

    val statusText: String
        get() = when (connectionState) {
            LiveKitConnectionState.DISCONNECTED -> "Disconnected"
            LiveKitConnectionState.CONNECTING -> "Connecting..."
            LiveKitConnectionState.CONNECTED -> "Connected to $roomName"
            LiveKitConnectionState.RECONNECTING -> "Reconnecting..."
            LiveKitConnectionState.ERROR -> errorMessage ?: "Error"
        }
}
