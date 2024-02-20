package com.example.colordetection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.example.colordetection.ui.theme.ColorDetectionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Timer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.schedule
import androidx.compose.ui.graphics.Color as ComposeColor

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

fun Image.toBitmap(): Bitmap {
    val planes = planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)

    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
@OptIn(ExperimentalGetImage::class) @Composable
fun CameraPreview() {
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var preview: Preview? by remember { mutableStateOf(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var colorDetected by remember { mutableStateOf<Color?>(null) }


    DisposableEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        preview = Preview.Builder().build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val bitmap = imageProxy.image?.toBitmap() ?: return@setAnalyzer

            val redThreshold = 150
            val redPixelCount = getPixelCount(bitmap, Color.Red, redThreshold)
            Log.d("Threshold red",redPixelCount.toString())

            val greenThreshold = 150
            val greenPixelCount = getPixelCount(bitmap, Color.Green, greenThreshold)
            Log.d("Threshold green",greenPixelCount.toString())

            val blueThreshold = 150
            val bluePixelCount = getPixelCount(bitmap, Color.Blue, blueThreshold)
            Log.d("Threshold blue",bluePixelCount.toString())

            colorDetected = when {
                redPixelCount > greenPixelCount && redPixelCount > bluePixelCount -> Color.Red
                greenPixelCount > redPixelCount && greenPixelCount > bluePixelCount -> Color.Green
                bluePixelCount > redPixelCount && bluePixelCount > greenPixelCount -> Color.Blue
                else -> null
            }

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
    ColorLabel(colorDetected)

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

fun getPixelCount(bitmap: Bitmap, targetColor: Color, threshold: Int): Int {
    val targetPixel = targetColor.toArgb()
    var pixelCount = 0

    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            val pixelColor = bitmap.getPixel(x, y)

            val contrast = ColorUtils.calculateContrast(pixelColor, targetPixel)
            Log.d("ColorDetection", "Contrast: $contrast")

            if (contrast >= threshold) {
                pixelCount++
            }
        }
    }

    return pixelCount
}

//fun getPixelCount(bitmap: Bitmap, targetColor: Color, threshold: Int): Int {
//    val targetPixel = targetColor.toArgb()
//    var pixelCount = 0
//
//    val width = bitmap.width
//    val height = bitmap.height
//
//    val pixels = IntArray(width * height)
//    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
//
//    for (pixelColor in pixels) {
//        val contrast = ColorUtils.calculateContrast(pixelColor, targetPixel)
//        if (contrast >= threshold) {
//            pixelCount++
//        }
//    }
//
//    return pixelCount
//}

@Composable
fun colorToString(color: Color): String {
    return when (color) {
        Color.Red -> "Red"
        Color.Green -> "Green"
        Color.Blue -> "Blue"
        else -> "Unknown"
    }
}
@Composable
fun ColorLabel(colorDetected: Color?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (colorDetected != null) {
            Text(
                text = "Detected Color: ${colorToString(colorDetected)}",
                style = TextStyle(color = Color.White, fontSize = 20.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.background(colorDetected, shape = RoundedCornerShape(8.dp))
            )
        } else {
            Text("No color detected", style = TextStyle(color = Color.White, fontSize = 20.sp))
        }
    }
}

