package com.nexatrode.nexawal.ui

import android.Manifest
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.nexatrode.nexawal.MoneroConfig
import com.nexatrode.nexawal.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    onScan: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val technoTheme = remember { MoneroConfig.isTechnoThemeEnabled(context) }
    val palette = rememberNexaPalette(technoTheme)
    val neon = palette.classic
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(onScan = onScan)
            // Neon scan frame overlay
            if (neon) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                        .border(BorderStroke(2.dp, palette.border), RoundedCornerShape(8.dp))
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    shape = RoundedCornerShape(if (neon) 28.dp else 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (neon) palette.cta else Color(0xFFFF6B35),
                        contentColor = if (neon) palette.ctaText else Color.White,
                    )
                ) {
                    Text(
                        stringResource(R.string.grant_camera),
                        fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(56.dp)
                    .a11yMinTouchTarget()
                    .clip(CircleShape)
                    .background(
                        if (neon) palette.border.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.3f)
                    )
                    .then(
                        if (neon) Modifier.border(BorderStroke(1.dp, palette.border), CircleShape)
                        else Modifier
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = if (neon) palette.accent else Color.White
                )
            }
        }

        val scanQrText = stringResource(R.string.scan_monero_qr)
        Text(
            text = if (neon) scanQrText.uppercase() else scanQrText,
            color = if (neon) palette.accent else Color.White,
            fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (neon) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (neon) 16.sp else 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
                .a11yHeading()
        )
    }
}

@Composable
private fun CameraPreview(
    onScan: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8",
                )
            )
        }
    }
    val handled = remember { AtomicBoolean(false) }
    val cameraPreviewDescription = stringResource(R.string.a11y_camera_preview)

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                try {
                    if (handled.get()) return@setAnalyzer
                    val text = decodeQr(imageProxy, reader) ?: return@setAnalyzer
                    if (handled.compareAndSet(false, true)) {
                        onScan(text)
                    }
                } finally {
                    imageProxy.close()
                }
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = cameraPreviewDescription }
    )
}

/**
 * Decode a QR payload from a CameraX YUV frame using FOSS ZXing (no ML Kit).
 */
private fun decodeQr(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    val luminance = yPlaneLuminance(imageProxy) ?: return null
    val (data, width, height) = luminance
    val source = PlanarYUVLuminanceSource(
        data,
        width,
        height,
        0,
        0,
        width,
        height,
        false,
    )
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        // Try inverted (light-on-dark codes).
        try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text
        } catch (_: NotFoundException) {
            null
        }
    } finally {
        reader.reset()
    }
}

/**
 * Copy the Y plane into a tightly packed buffer, applying CameraX rotation so
 * ZXing sees an upright image.
 */
private fun yPlaneLuminance(imageProxy: ImageProxy): Triple<ByteArray, Int, Int>? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val width = imageProxy.width
    val height = imageProxy.height
    val buffer = plane.buffer.duplicate()
    buffer.rewind()

    val packed = ByteArray(width * height)
    if (pixelStride == 1 && rowStride == width) {
        buffer.get(packed, 0, packed.size.coerceAtMost(buffer.remaining()))
    } else {
        var out = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                packed[out++] = buffer.get(rowStart + col * pixelStride)
            }
        }
    }

    return when (imageProxy.imageInfo.rotationDegrees) {
        90 -> Triple(rotate90(packed, width, height), height, width)
        180 -> Triple(rotate180(packed, width, height), width, height)
        270 -> Triple(rotate270(packed, width, height), height, width)
        else -> Triple(packed, width, height)
    }
}

private fun rotate90(src: ByteArray, width: Int, height: Int): ByteArray {
    val dst = ByteArray(src.size)
    var i = 0
    for (x in 0 until width) {
        for (y in height - 1 downTo 0) {
            dst[i++] = src[y * width + x]
        }
    }
    return dst
}

private fun rotate180(src: ByteArray, @Suppress("UNUSED_PARAMETER") width: Int, @Suppress("UNUSED_PARAMETER") height: Int): ByteArray {
    val dst = ByteArray(src.size)
    for (i in src.indices) {
        dst[dst.lastIndex - i] = src[i]
    }
    return dst
}

private fun rotate270(src: ByteArray, width: Int, height: Int): ByteArray {
    val dst = ByteArray(src.size)
    var i = 0
    for (x in width - 1 downTo 0) {
        for (y in 0 until height) {
            dst[i++] = src[y * width + x]
        }
    }
    return dst
}
