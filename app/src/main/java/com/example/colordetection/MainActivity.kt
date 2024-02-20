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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalGetImage::class) @Composable
fun CameraPreview() {
//    var bitmap: Bitmap? = null
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var preview: Preview? by remember { mutableStateOf(null) }
    var colorHash by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var colorDetected by remember { mutableStateOf<Color?>(null) }
//    var averageColor: ComposeColor? by remember { mutableStateOf(null) }


    DisposableEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        preview = Preview.Builder().build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

//        imageAnalysis.setAnalyzer(
//            ContextCompat.getMainExecutor(context)
//        ) { imageProxy ->
//            val averageColor = calculateAverageColor(imageProxy)
//            colorHash = colorToHex(averageColor)
//
////            if (isRed(averageColor) || isGreen(averageColor) || isBlue(averageColor)) {
////                displayLabelOnCamera(colorHash ?: "")
////            }
//
//            imageProxy.close()
//        }
fun Image.toBitmap(): Bitmap {
    val planes = planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    // Copy Y
    yBuffer.get(nv21, 0, ySize)
    // Copy UV
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)

    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val bitmap = imageProxy.image?.toBitmap() ?: return@setAnalyzer

            // Example: Detecting Red Color
            val redThreshold = 150
            val redPixelCount = getPixelCount(bitmap, Color.Red, redThreshold)

            // Example: Detecting Green Color
            val greenThreshold = 150
            val greenPixelCount = getPixelCount(bitmap, Color.Green, greenThreshold)

            // Example: Detecting Blue Color
            val blueThreshold = 150
            val bluePixelCount = getPixelCount(bitmap, Color.Blue, blueThreshold)

            // Adjust the thresholds and conditions based on your specific color detection requirements

            colorDetected = when {
                redPixelCount > greenPixelCount && redPixelCount > bluePixelCount -> Color.Red
                greenPixelCount > redPixelCount && greenPixelCount > bluePixelCount -> Color.Green
                bluePixelCount > redPixelCount && bluePixelCount > greenPixelCount -> Color.Blue
                else -> null
            }

            imageProxy.close()
        }
//        imageAnalysis.setAnalyzer(
//            ContextCompat.getMainExecutor(context)
//        ) { imageProxy ->
//            // Run the suspend function in a coroutine
//            lifecycleOwner.lifecycleScope.launch {
//                averageColor = calculateAverageColor(imageProxy)
//                colorHash = colorToHex(averageColor!!)
//            }
////            if (averageColor?.let { isRed(it) } == true || averageColor?.let { isGreen(it) } == true || averageColor?.let { isBlue(it) } == true) {
////                displayLabelOnCamera(colorHash ?: "")
////            }
//            imageProxy.close()
//        }

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

fun getPixelCount(bitmap: Bitmap, targetColor: Color, threshold: Int): Int {
    val targetPixel = targetColor.toArgb()
    var pixelCount = 0

    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            val pixelColor = bitmap.getPixel(x, y)

            if (ColorUtils.calculateContrast(pixelColor, targetPixel) >= threshold) {
                pixelCount++
            }
        }
    }

    return pixelCount
}
// ...

// fun calculateAverageColor(image: ImageProxy): Color {
//    val buffer = image.planes[0].buffer
//    val pixelStride = image.planes[0].pixelStride
//    val rowStride = image.planes[0].rowStride
//    val rowPadding = rowStride - pixelStride * image.width
//    val pixels = IntArray(image.width * image.height)
//
//    var offset = 0
//    for (row in 0 until image.height) {
//        for (col in 0 until image.width) {
//            val pixel =
//                (buffer.get(offset).toInt() and 0xFF) or
//                        ((buffer.get(offset + 1).toInt() and 0xFF) shl 8) or
//                        ((buffer.get(offset + 2).toInt() and 0xFF) shl 16) or
//                        ((buffer.get(offset + 3).toInt() and 0xFF) shl 24)
//
//            pixels[row * image.width + col] = pixel
//            Log.d("Cal", "${ pixels.average()}")
//            offset += pixelStride
//        }
//        offset += rowPadding
//    }
//
//    if (pixels.isNotEmpty()) {
//        val red = pixels.map { Color(it).red }.average()
//        val green = pixels.map { Color(it).green }.average()
//        val blue = pixels.map { Color(it).blue }.average()
//
//        return Color(red.toFloat(), green.toFloat(), blue.toFloat())
//    } else {
//        return Color.White
//    }
//}


//fun calculateAverageColor(image: ImageProxy): Color {
////    Log.d("Working", "Working")
//    val buffer = image.planes[0].buffer
//    val pixelStride = image.planes[0].pixelStride
//    val rowStride = image.planes[0].rowStride
//    val rowPadding = rowStride - pixelStride * image.width
//    val pixels = IntArray(image.width * image.height)
////    if (buffer.remaining() < pixels.average()) {
////        Log.d("Working", "Working")
//        Log.d("Size of Buffer", buffer.toString())
////    buffer.rewind()
//        var offset = 0
//        for (row in 0 until image.height) {
//            for (col in 0 until image.width) {
//                val pixel = (buffer.get(offset).toInt() and 0xFF) or
//                            ((buffer.get(offset + 1).toInt() and 0xFF) shl 8) or
//                            ((buffer.get(offset + 2).toInt() and 0xFF) shl 16) or
//                            ((buffer.get(offset + 3).toInt() and 0xFF) shl 24)
//
//                pixels[row * image.width + col ] = pixel
//                val pix = pixels.average()
//                Log.d("Cal", "${ pixels.average()}")
//                pixels.average()
//                offset += pixelStride
//            }
//            offset += rowPadding
//        }
//        if (pixels.isNotEmpty()) {
//            Log.d("Working", "Working")
//            val red = pixels.map { Color(it).red }.average()
//            val green = pixels.map { Color(it).green }.average()
//            val blue = pixels.map { Color(it).blue }.average()
//
//            return Color(red.toFloat(), green.toFloat(), blue.toFloat())
//        } else {
//            return Color.White
//        }
//
////    }
////    else{
////        return Color.White
////    }
//}

//fun calculateAverageColor(image: ImageProxy): Color {
//    val buffer = image.planes[0].buffer
//    val pixelStride = image.planes[0].pixelStride
//    val rowStride = image.planes[0].rowStride
//    val rowPadding = rowStride - pixelStride * image.width
//    val pixels = IntArray(image.width * image.height)
//
//    var totalRed = 0
//    var totalGreen = 0
//    var totalBlue = 0
//
//    buffer.rewind()
//    var offset = 0
//
//    for (row in 0 until image.height) {
//        for (col in 0 until image.width) {
//            val pixel = image.getPixel(col, row)
//
//            val blue = Color.Blue
//            val green = Color.Green
//            val red = Color.Red
//
//            val intensity = (red + green + blue) / 3.0 / 255.0
//            val minColor = minOf(red, green, blue) / 255.0
//            val maxColor = maxOf(red, green, blue) / 255.0
//
//            val delta = maxColor - minColor
//
//            val saturation = if (maxColor == 0.0) 0.0 else delta / maxColor
//            val hue = when {
//                delta == 0.0 -> 0.0 // undefined, set to 0
//                maxColor == red / 255.0 -> 60.0 * ((green / 255.0 - blue / 255.0) / delta % 6)
//                maxColor == green / 255.0 -> 60.0 * ((blue / 255.0 - red / 255.0) / delta + 2)
//                else -> 60.0 * ((red / 255.0 - green / 255.0) / delta + 4)
//            }
//
//            val newPixel = Color.(255, hue.toInt() and 0xFF, (saturation * 255).toInt(), (intensity * 255).toInt())
//            pixels[row * image.width + col] = newPixel
//
//            totalBlue += blue
//            totalGreen += green
//            totalRed += red
//        }
//    }
//
//    val pixelCount = image.width * image.height
//    val averageRed = totalRed / pixelCount
//    val averageGreen = totalGreen / pixelCount
//    val averageBlue = totalBlue / pixelCount
//
//    return Color(averageRed.toFloat() / 255, averageGreen.toFloat() / 255, averageBlue.toFloat() / 255)
//
//}
fun colorToHex(color: Color): String {

    val red = (color.red * 255).toInt().toString(16).padStart(2, '0')
    val green = (color.green * 255).toInt().toString(16).padStart(2, '0')
    val blue = (color.blue * 255).toInt().toString(16).padStart(2, '0')
    return "$red$green$blue"
}

@Composable
fun displayLabelOnCamera(label: String) {
    Text(
        text = label,
        color = Color.White,
        modifier = Modifier
            .padding(16.dp)
            .background(Color.Black)
    )
}

fun isRed(color: Color): Boolean {
    return color.red > 0.8 && color.green < 0.2 && color.blue < 0.2
}

fun isGreen(color: Color): Boolean {
    return color.red < 0.2 && color.green > 0.8 && color.blue < 0.2
}

fun isBlue(color: Color): Boolean {
    return color.red < 0.2 && color.green < 0.2 && color.blue > 0.8
}

