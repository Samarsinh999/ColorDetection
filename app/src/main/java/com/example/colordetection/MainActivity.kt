package com.example.colordetection

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.colordetection.ui.theme.ColorDetectionTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraPreview()
                }
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun CameraPreview() {
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var preview: Preview? by remember { mutableStateOf(null) }
    var colorHash by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    DisposableEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        preview = Preview.Builder().build()


        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            ContextCompat.getMainExecutor(context)
        ) { imageProxy ->
            val averageColor = calculateAverageColor(imageProxy)
            colorHash = colorToHex(averageColor)
            imageProxy.close()
        }
        cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )

        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        factory = { ctx ->
           PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setImplementationMode(androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Set a background color if needed
    ) { previewView ->
        // Set the preview surface on the PreviewView
        preview?.setSurfaceProvider(previewView.surfaceProvider)
    }
}

fun calculateAverageColor(image: ImageProxy): Color {
    val buffer = image.planes[0].buffer
    val pixelStride = image.planes[0].pixelStride
    val rowStride = image.planes[0].rowStride
    val rowPadding = rowStride - pixelStride * image.width

    val pixels = IntArray(image.width * image.height)

    var offset = 0
    for (row in 0 until image.height) {
        for (col in 0 until image.width) {
            val pixel = (buffer.get(offset).toInt() and 0xFF shl 16) or
                    (buffer.get(offset + 1).toInt() and 0xFF shl 8) or
                    (buffer.get(offset + 2).toInt() and 0xFF) or
                    (buffer.get(offset + 3).toInt() and 0xFF shl 24)
            pixels[row * image.width + col] = pixel
            offset += pixelStride
        }
        offset += rowPadding
    }

    val red = pixels.map { Color(it).red }.average()
    val green = pixels.map { Color(it).green }.average()
    val blue = pixels.map { Color(it).blue }.average()

    return Color(red.toFloat(), green.toFloat(), blue.toFloat())
}

fun colorToHex(color: Color): String {
    val red = (color.red * 255).toInt().toString(16).padStart(2, '0')
    val green = (color.green * 255).toInt().toString(16).padStart(2, '0')
    val blue = (color.blue * 255).toInt().toString(16).padStart(2, '0')
    return "$red$green$blue"
}

