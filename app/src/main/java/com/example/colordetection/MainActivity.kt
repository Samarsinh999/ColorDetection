package com.example.colordetection

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.colordetection.ui.theme.ColorDetectionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorDetectionTheme {
                Surface(
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

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalGetImage::class) @Composable
fun CameraPreview() {
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var preview: Preview? by remember { mutableStateOf(null) }
    var colorHash by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var averageColor by remember { mutableStateOf<Color?>(null) }
//    var colorDetected by remember { mutableStateOf<Color?>(null) }
//    var backgroundColor by remember { mutableStateOf(Color.Gray) }
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
            lifecycleOwner.lifecycleScope.launch {
                        val colorsList = calculateAverageColor(imageProxy)
                Log.d("ColorList", colorsList.size.toString())
                        for ((rowIndex, rowColors) in colorsList.withIndex()) {
                            Log.d("ColorList", "Number of Colors in Row $rowIndex: ${rowColors.size}")
                            for ((colIndex, color) in rowColors.withIndex()) {
                                Log.d("ColorList", "Color at ($rowIndex, $colIndex): $color")
                            }
                        }
                        imageProxy.close()
            }
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
//    displayLabelOnCamera("")
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
            .background(Color.Black)
    ) { previewView ->
        preview?.setSurfaceProvider(previewView.surfaceProvider)
    }
}

/**
 *
 */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun calculateAverageColor(image: ImageProxy): List<List<Color>> = withContext(Dispatchers.Default) {
    val buffer = image.planes[0].buffer
    val pixelStride = image.planes[0].pixelStride
    val rowStride = image.planes[0].rowStride
    val rowPadding = rowStride - pixelStride * image.width

    val colorsList = mutableListOf<List<Color>>()

    for (row in 0 until image.height) {
        val rowColors = mutableListOf<Color>()
        for (col in 0 until image.width) {
            val offset = row * rowStride + col * pixelStride
            if (offset < buffer.capacity()) {
                val pixel =
                    ((buffer.get(offset).toInt() and 0xFF) shl 24) or
                            ((buffer.get(offset + 1).toInt() and 0xFF) shl 16) or
                            ((buffer.get(offset + 2).toInt() and 0xFF) shl 8) or
                            (buffer.get(offset + 3).toInt() and 0xFF)

                val color = Color(pixel)
                rowColors.add(color)
                Log.d("Color Details", "Color at offset ($row, $col): $color")
            } else {
                Log.e("Color Details", "Error: Index out of bounds at offset $offset")
            }
        }
        colorsList.add(rowColors)
    }
    return@withContext colorsList
}



//@RequiresApi(Build.VERSION_CODES.O)
//suspend fun calculateAverageColor(image: ImageProxy): Color = withContext(Dispatchers.Default) {
//    val buffer = image.planes[0].buffer
//    val pixelStride = image.planes[0].pixelStride
//    val rowStride = image.planes[0].rowStride
//    val rowPadding = rowStride - pixelStride * image.width
//
//    val pixels = IntArray(image.width * image.height)
//    var offset = 0
//            if (offset < buffer.remaining()) {
////                var offset1 = row * rowStride + col * pixelStride
//                val pixel =
//                    ((buffer.get(offset++).toInt() and 0xFF) shl 24) or
//                            ((buffer.get(offset++).toInt() and 0xFF) shl 16) or
//                            ((buffer.get(offset++).toInt() and 0xFF) shl 8) or
//                            (buffer.get(offset).toInt() and 0xFF)
//
////                pixels[row * image.width + col] = pixel
//                Log.d("PixelProcessing", "Pixel value: $pixel")
//                Log.d("Offset", "Offset value: $offset")
//                offset = rowStride * pixelStride
//                offset += pixelStride
//                Log.d("pixeStride", " value: $offset")
//            } else {
//                Log.e("ImageProcessing", "Error: Index out of bounds at offset $offset")
//                return@withContext Color.White
//            }
////        }
//        offset += rowPadding
//    Log.d("rowPadding", " value: $rowPadding")
////    }
//
//    if (pixels.isNotEmpty()) {
//        val totalRed = pixels.map { Color(it).red }.sum()
//        val totalGreen = pixels.map { Color(it).green }.sum()
//        val totalBlue = pixels.map { Color(it).blue }.sum()
//
//        val averageRed = (totalRed / pixels.size)
//        val averageGreen = (totalGreen / pixels.size)
//        val averageBlue = (totalBlue / pixels.size)
//        Log.d("Red", "value: $averageRed")
//        return@withContext Color(averageRed, averageGreen, averageBlue)
//    } else {
//        Log.e("ImageProcessing", "Error: No pixels found")
//        return@withContext Color.White
//    }
//}

fun applySepiaFilter(color: Color): Color {
    val outputRed = (color.red * 0.393 + color.green * 0.769 + color.blue * 0.189).coerceIn(0.0, 255.0)
    val outputGreen = (color.red * 0.349 + color.green * 0.686 + color.blue * 0.168).coerceIn(0.0, 255.0)
    val outputBlue = (color.red * 0.272 + color.green * 0.534 + color.blue * 0.131).coerceIn(0.0, 255.0)

    return Color(outputRed.toInt(), outputGreen.toInt(), outputBlue.toInt())
}

fun applyGrayscaleFilter(color: Color): Color {
    val averageValue = (color.red + color.green + color.blue) / 3
    return Color(averageValue.toInt(), averageValue.toInt(), averageValue.toInt())
}

fun adjustBrightness(color: Color, factor: Float): Color {
    val outputRed = (color.red * factor).coerceIn(0.0F, 255.0F)
    val outputGreen = (color.green * factor).coerceIn(0.0F, 255.0F)
    val outputBlue = (color.blue * factor).coerceIn(0.0F, 255.0F)

    return Color(outputRed.toInt(), outputGreen.toInt(), outputBlue.toInt())
}

//suspend fun calculateAverageColor(image: ImageProxy): Color = withContext(Dispatchers.Default) {
//    val buffer = image.planes[0].buffer
//    val pixelStride = image.planes[0].pixelStride
//    val rowStride = image.planes[0].rowStride
//    val rowPadding = rowStride - pixelStride * image.width
//    val pixels = IntArray(image.width * image.height)
//    var offset = 0
////    if (buffer.remaining() < pixels.average()) {
//        for (row in 0 until image.height) {
//            for (col in 0 until image.width) {
//                var offset1 = row * rowStride + col * pixelStride
////                Log.d("PixelProcessing", "Processing pixel at offset: $offset1, row: $row, col: $col")
//                val pixel =
//                    ((buffer.get(offset1 ++).toInt() and 0xFF) shl 24)or
//                            ((buffer.get(offset1 ++).toInt() and 0xFF) shl 16) or
//                            ((buffer.get(offset1 ++).toInt() and 0xFF) shl 8) or
//                            (buffer.get(offset1).toInt() and 0xFF)
//                pixels[row * image.width + col] = pixel
//                Log.d("PixelProcessing", "Pixel value: $pixel")
//                run {
//                    val totalRed = pixels.map { Color(it).red }.sum()
//                    val totalGreen = pixels.map { Color(it).green }.sum()
//                    val totalBlue = pixels.map { Color(it).blue }.sum()
//                    Log.d("totalRed", totalRed.toString())
//                    val averageRed = (totalRed / pixels.size)
//                    val averageGreen = (totalGreen / pixels.size)
//                    val averageBlue = (totalBlue / pixels.size)
//
//                    return@withContext Color(averageRed, averageGreen, averageBlue)
//                }
////               Log.d("Pixel" ,pixels.average().toString())
//                offset1 += pixelStride
//            }
//            offset += rowPadding
//        }
//
//    run {
//        val totalRed = pixels.map { Color(it).red }.sum()
//        val totalGreen = pixels.map { Color(it).green }.sum()
//        val totalBlue = pixels.map { Color(it).blue }.sum()
//        Log.d("totalRed", totalRed.toString())
//        val averageRed = (totalRed / pixels.size)
//        val averageGreen = (totalGreen / pixels.size)
//        val averageBlue = (totalBlue / pixels.size)
//
//        return@withContext Color(averageRed, averageGreen, averageBlue)
//    }
////    } else {
////        return@withContext Color.White
////    }
//}

//suspend fun calculateAverageColor(image: ImageProxy): Color = withContext(Dispatchers.Default) {
//    val buffer = image.planes[0].buffer
//    val pixelStride = image.planes[0].pixelStride
//    val rowStride = image.planes[0].rowStride
//    val rowPadding = rowStride - pixelStride * image.width
//
//    val pixels = IntArray(image.width * image.height)
//    var offset = rowStride + pixelStride
//
//    for (row in 0 until image.height) {
//        for (col in 0 until image.width) {
////            var offset1 = row * rowStride + col * pixelStride
//            val pixel =
//                ((buffer.get(offset++).toInt() and 0xFF) shl 24) or
//                        ((buffer.get(offset++).toInt() and 0xFF) shl 16) or
//                        ((buffer.get(offset++).toInt() and 0xFF) shl 8) or
//                        (buffer.get(offset).toInt() and 0xFF)
//
//            pixels[row * image.width+ col] = pixel
//            Log.d("PixelProcessing", "Pixel value: ${pixel}")
//        }
//        offset += rowPadding
//    }
//
//    val totalRed = pixels.map { Color(it).red }.sum()
//    val totalGreen = pixels.map { Color(it).green }.sum()
//    val totalBlue = pixels.map { Color(it).blue }.sum()
//    Log.d("totalRed", totalRed.toString())
//
//    val averageRed = (totalRed / pixels.size)
//    val averageGreen = (totalGreen / pixels.size)
//    val averageBlue = (totalBlue / pixels.size)
//
//    return@withContext Color(averageRed, averageGreen, averageBlue)
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
