package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.ObsidianVoid

@Composable
fun CameraScanOverlay(
    continuousWatchEnabled: Boolean,
    onToggleContinuousWatch: (Boolean) -> Unit,
    onFrameCaptured: (Bitmap, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = MaterialTheme.colorScheme.primary

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(lensFacing, previewView) {
        val view = previewView ?: return@DisposableEffect onDispose {}
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = view.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            } catch (e: Exception) {
                Log.e("CameraScanOverlay", "CameraX bind failed", e)
            }
        }
        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .safeDrawingPadding()
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = pv
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Holographic optical targeting reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.28f
            drawCircle(
                color = accent.copy(alpha = 0.55f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                color = accent.copy(alpha = 0.65f),
                start = Offset(cx - r - 24f, cy),
                end = Offset(cx - r + 14f, cy),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = accent.copy(alpha = 0.65f),
                start = Offset(cx + r - 14f, cy),
                end = Offset(cx + r + 24f, cy),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Top HUD overlay
        Surface(
            color = ObsidianPanel.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("CAMERA OPTICAL SCAN", style = MaterialTheme.typography.titleSmall, color = accent)
                    Text("Identify objects, code, or handwritten notes", style = MaterialTheme.typography.labelSmall, color = HudTextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera lens", tint = accent)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_camera_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close optical scanner", tint = HudTextPrimary)
                    }
                }
            }
        }

        // Bottom controls
        Surface(
            color = ObsidianPanel.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChip(
                    selected = continuousWatchEnabled,
                    onClick = { onToggleContinuousWatch(!continuousWatchEnabled) },
                    leadingIcon = {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = {
                        Text(
                            text = if (continuousWatchEnabled) "Watch ON" else "Continuous",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (continuousWatchEnabled) AmberAlert else HudTextPrimary
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        val capture = imageCapture ?: return@Button
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val bmp = image.toRotatedBitmap()
                                        onFrameCaptured(bmp, true)
                                    } finally {
                                        image.close()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraScanOverlay", "Image capture failed", exception)
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ObsidianVoid),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("capture_analyze_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SCAN WITH JARVIS", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun ImageProxy.toRotatedBitmap(): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (rotated != raw) raw.recycle()
    return rotated
}
