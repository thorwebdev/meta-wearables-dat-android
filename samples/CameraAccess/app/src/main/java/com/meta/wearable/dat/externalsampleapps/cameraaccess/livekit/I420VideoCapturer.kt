/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.livekit

import livekit.org.webrtc.CapturerObserver
import livekit.org.webrtc.JavaI420Buffer
import livekit.org.webrtc.SurfaceTextureHelper
import livekit.org.webrtc.VideoCapturer
import livekit.org.webrtc.VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Custom VideoCapturer that pushes I420 frames to LiveKit
 * 
 * This capturer receives I420 frames from the DAT SDK camera stream
 * and sends them to LiveKit.
 */
class I420VideoCapturer(
    private val targetWidth: Int = 640,
    private val targetHeight: Int = 480,
    private val targetFps: Int = 24
) : VideoCapturer {
    
    companion object {
        private const val TAG = "I420VideoCapturer"
    }
    
    private var capturerObserver: CapturerObserver? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private val isRunning = AtomicBoolean(false)
    
    private var frameCount = 0L
    private var lastFrameTime = System.nanoTime()
    private val frameIntervalNs = 1_000_000_000L / targetFps
    
    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        context: android.content.Context?,
        capturerObserver: CapturerObserver?
    ) {
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
        
        android.util.Log.d(TAG, "Initialized with ${targetWidth}x${targetHeight} @ ${targetFps}fps")
    }
    
    override fun startCapture(width: Int, height: Int, framerate: Int) {
        isRunning.set(true)
        android.util.Log.d(TAG, "Started capture")
    }
    
    override fun stopCapture() {
        isRunning.set(false)
        android.util.Log.d(TAG, "Stopped capture")
    }
    
    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        android.util.Log.d(TAG, "Change capture format: ${width}x${height} @ ${framerate}fps")
    }
    
    override fun dispose() {
        isRunning.set(false)
        capturerObserver = null
        surfaceTextureHelper = null
        android.util.Log.d(TAG, "Disposed")
    }
    
    override fun isScreencast(): Boolean = false
    
    /**
     * Called when a raw I420 frame is available from DAT SDK
     */
    fun pushI420(
        width: Int,
        height: Int,
        dataY: ByteBuffer,
        strideY: Int,
        dataU: ByteBuffer,
        strideU: Int,
        dataV: ByteBuffer,
        strideV: Int,
        rotationDegrees: Int,
        timestampNs: Long
    ) {
        if (!isRunning.get()) {
            return
        }

        // Rate limiting
        val now = System.nanoTime()
        if (now - lastFrameTime < frameIntervalNs) {
            return
        }
        lastFrameTime = now

        val i420Buffer = JavaI420Buffer.wrap(
            width,
            height,
            dataY, strideY,
            dataU, strideU,
            dataV, strideV,
            null
        )
        val frame = VideoFrame(i420Buffer, rotationDegrees, timestampNs)

        // Send the video frame to LiveKit
        capturerObserver?.onFrameCaptured(frame)
        
        frame.release()

        frameCount++
        if (frameCount % 100 == 0L) {
            android.util.Log.d(TAG, "Sent $frameCount frames")
        }
    }
}

