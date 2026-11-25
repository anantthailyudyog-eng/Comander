package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ScreenLinkController"

object ScreenLinkController {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameAtMs = 0L

    fun startProjection(context: Context, resultCode: Int, data: Intent) {
        stopProjection(context)
        try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data) ?: return
            mediaProjection = projection

            val metrics = context.resources.displayMetrics
            val targetWidth = (metrics.widthPixels / 2).coerceIn(360, 720)
            val targetHeight = (metrics.heightPixels / 2).coerceIn(640, 1280)
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopProjection(context)
                }
            }, handler)

            reader.setOnImageAvailableListener({ r ->
                val now = System.currentTimeMillis()
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (now - lastFrameAtMs < 450L) return@setOnImageAvailableListener
                    lastFrameAtMs = now
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * targetWidth

                    val rawBitmap = Bitmap.createBitmap(
                        targetWidth + rowPadding / pixelStride,
                        targetHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, targetWidth, targetHeight)
                    if (rawBitmap != cropped) rawBitmap.recycle()
                    _latestFrame.value = cropped
                } catch (e: Exception) {
                    Log.w(TAG, "Frame decode error: ${e.message}")
                } finally {
                    image.close()
                }
            }, handler)

            virtualDisplay = projection.createVirtualDisplay(
                "JarvisHudScreenLink",
                targetWidth,
                targetHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
            _isActive.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection", e)
            stopProjection(context)
        }
    }

    fun stopProjection(context: Context? = null) {
        _isActive.value = false
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        context?.let {
            try {
                it.stopService(Intent(it, ScreenLinkService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    fun clearPreviewFrame() {
        _latestFrame.value = null
    }

    fun launchService(context: Context, resultCode: Int, data: Intent) {
        val serviceIntent = Intent(context, ScreenLinkService::class.java).apply {
            putExtra(ScreenLinkService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenLinkService.EXTRA_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
