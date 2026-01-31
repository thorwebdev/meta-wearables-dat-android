/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.livekit

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteVideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * LiveKit connection state
 */
enum class LiveKitConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * LiveKit Manager - Handles WebRTC connection to LiveKit server
 *
 * This manager handles:
 * - Connection to LiveKit server
 * - Publishing video frames from Ray-Ban Meta glasses
 * - Publishing audio from phone microphone
 * - Receiving audio responses from AI agent
 */
class LiveKitManager(private val context: Context) {

    companion object {
        private const val TAG = "LiveKitManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private var localAudioTrack: LocalAudioTrack? = null
    private var videoCapturer: BitmapVideoCapturer? = null

    private var serverUrl: String = ""
    private var token: String = ""

    private var eventCollectJob: Job? = null

    // State flows
    private val _connectionState = MutableStateFlow(LiveKitConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LiveKitConnectionState> = _connectionState.asStateFlow()

    private val _remoteAudioTrack = MutableStateFlow<RemoteAudioTrack?>(null)
    val remoteAudioTrack: StateFlow<RemoteAudioTrack?> = _remoteAudioTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<RemoteVideoTrack?>(null)
    val remoteVideoTrack: StateFlow<RemoteVideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Configure LiveKit server connection
     */
    fun configure(serverUrl: String, token: String) {
        this.serverUrl = serverUrl
        this.token = token
    }

    /**
     * Connect to LiveKit room
     */
    suspend fun connect(roomName: String, participantName: String): Boolean {
        if (_connectionState.value == LiveKitConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected")
            return true
        }

        if (serverUrl.isEmpty() || token.isEmpty()) {
            val errorMsg = "Server URL or token is not configured."
            _errorMessage.value = errorMsg
            Log.e(TAG, errorMsg)
            _connectionState.value = LiveKitConnectionState.ERROR
            return false
        }

        _connectionState.value = LiveKitConnectionState.CONNECTING

        return try {
            // Create room
            room = LiveKit.create(context)

            // Setup event listeners
            setupRoomEventListeners()

            // Connect to room
            room?.connect(serverUrl, token)

            _connectionState.value = LiveKitConnectionState.CONNECTED
            Log.d(TAG, "Connected to room: $roomName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            _connectionState.value = LiveKitConnectionState.ERROR
            _errorMessage.value = "Connection failed: ${e.message}"
            false
        }
    }

    /**
     * Disconnect from LiveKit room
     */
    fun disconnect() {
        scope.launch {
            try {
                stopPublishingVideo()
                stopPublishingAudio()
                eventCollectJob?.cancel()
                room?.disconnect()
                room = null
                _connectionState.value = LiveKitConnectionState.DISCONNECTED
                _remoteAudioTrack.value = null
                _remoteVideoTrack.value = null
                Log.d(TAG, "Disconnected from room")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting: ${e.message}", e)
            }
        }
    }

    /**
     * Start publishing video track with bitmap frames
     */
    suspend fun startPublishingVideo(width: Int = 640, height: Int = 480, fps: Int = 24): Boolean {
        val currentRoom = room ?: run {
            Log.e(TAG, "Not connected to room")
            return false
        }

        return try {
            // Create bitmap video capturer
            videoCapturer = BitmapVideoCapturer(width, height, fps)

            // Create local video track
            localVideoTrack = currentRoom.localParticipant.createVideoTrack(
                name = "glasses-camera",
                capturer = videoCapturer!!
            )

            // Publish track
            currentRoom.localParticipant.publishVideoTrack(localVideoTrack!!)

            // Force start capture - LiveKit may not call startCapture() automatically
            videoCapturer?.startCapture(width, height, fps)

            Log.d(TAG, "Started publishing video track: ${localVideoTrack!!.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish video: ${e.message}", e)
            false
        }
    }

    /**
     * Stop publishing video track
     */
    fun stopPublishingVideo() {
        localVideoTrack?.let { track ->
            room?.localParticipant?.unpublishTrack(track)
            track.stop()
        }
        videoCapturer?.dispose()
        videoCapturer = null
        localVideoTrack = null
        Log.d(TAG, "Stopped publishing video")
    }

    /**
     * Send a video frame (bitmap) to LiveKit
     */
    fun sendVideoFrame(bitmap: Bitmap) {
        videoCapturer?.onBitmapFrame(bitmap)
    }

    /**
     * Start publishing audio from phone microphone
     */
    suspend fun startPublishingAudio(): Boolean {
        val currentRoom = room ?: run {
            Log.e(TAG, "Not connected to room")
            return false
        }

        return try {
            // Create local audio track from microphone
            localAudioTrack = currentRoom.localParticipant.createAudioTrack(
                name = "phone-microphone"
            )

            // Publish track
            currentRoom.localParticipant.publishAudioTrack(localAudioTrack!!)

            Log.d(TAG, "Started publishing audio track")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish audio: ${e.message}", e)
            false
        }
    }

    /**
     * Stop publishing audio track
     */
    fun stopPublishingAudio() {
        localAudioTrack?.let { track ->
            room?.localParticipant?.unpublishTrack(track)
            track.stop()
        }
        localAudioTrack = null
        Log.d(TAG, "Stopped publishing audio")
    }

    /**
     * Mute/unmute local audio
     */
    fun setAudioMuted(muted: Boolean) {
        localAudioTrack?.let { track ->
            scope.launch {
                room?.localParticipant?.setMicrophoneEnabled(!muted)
            }
        }
    }

    /**
     * Setup room event listeners
     */
    private fun setupRoomEventListeners() {
        eventCollectJob = scope.launch {
            room?.events?.collect { event ->
                when (event) {
                    is RoomEvent.Disconnected -> {
                        Log.d(TAG, "Room disconnected")
                        _connectionState.value = LiveKitConnectionState.DISCONNECTED
                    }
                    is RoomEvent.Reconnecting -> {
                        Log.d(TAG, "Room reconnecting")
                        _connectionState.value = LiveKitConnectionState.RECONNECTING
                    }
                    is RoomEvent.Reconnected -> {
                        Log.d(TAG, "Room reconnected")
                        _connectionState.value = LiveKitConnectionState.CONNECTED
                    }
                    is RoomEvent.TrackSubscribed -> {
                        Log.d(TAG, "Track subscribed: ${event.track.kind}, name: ${event.track.name}, participant: ${event.participant?.identity}")
                        when (event.track) {
                            is RemoteAudioTrack -> {
                                _remoteAudioTrack.value = event.track as RemoteAudioTrack
                                Log.d(TAG, "Remote audio track received from ${event.participant?.identity}")
                            }
                            is RemoteVideoTrack -> {
                                _remoteVideoTrack.value = event.track as RemoteVideoTrack
                                Log.d(TAG, "Remote video track received from ${event.participant?.identity}")
                            }
                            else -> {
                                Log.d(TAG, "Other track type subscribed: ${event.track.kind}")
                            }
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        Log.d(TAG, "Track unsubscribed: ${event.track.kind}")
                        when (event.track) {
                            is RemoteAudioTrack -> {
                                _remoteAudioTrack.value = null
                            }
                            is RemoteVideoTrack -> {
                                _remoteVideoTrack.value = null
                            }
                            else -> {
                                // Other track types
                            }
                        }
                    }
                    is RoomEvent.ParticipantConnected -> {
                        Log.d(TAG, "Participant connected: ${event.participant.identity}")
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        Log.d(TAG, "Participant disconnected: ${event.participant.identity}")
                    }
                    else -> {
                        // Handle other events if needed
                    }
                }
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}
