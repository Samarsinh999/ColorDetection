package com.example.colordetection

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.example.colordetection.ui.theme.ColorDetectionTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@Composable
fun SampleView() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    val cameraController = LifecycleCameraController(context).apply {
        bindToLifecycle(lifecycleOwner)
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }
    val previewView: PreviewView = PreviewView(context).apply {
        controller = cameraController
    }

    val preview = Preview.Builder().build()
    val cameraProvider = ProcessCameraProvider.getInstance(context)
//    val viewFinder: PreviewView = findViewById(R.id.previewView)

// The use case is bound to an Android Lifecycle with the following code
    val camera = cameraProvider.get().bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)

// PreviewView creates a surface provider and is the recommended provider


    AndroidView(factory = { _ ->
        previewView
    }) { view ->
        preview.setSurfaceProvider(view.surfaceProvider)

    }


//    preview.setSurfaceProvider(viewFinder.getSurfaceProvider())
}

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorDetectionTheme {
                SampleView()
//                CameraPreview()
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProvider by remember {
        mutableStateOf(ProcessCameraProvider.getInstance(context))
    }

    val preview: Preview by remember { mutableStateOf(Preview.Builder().build()) }

    DisposableEffect(context) {

        Log.d("dispose check ", "calling")

        cameraProvider.get().bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview
        )
        onDispose {
            cameraProvider.get().unbindAll()
        }

    }


    AndroidView(factory = { _ ->
        androidx.camera.view.PreviewView(context)
    }) { view ->
        preview.setSurfaceProvider(view.surfaceProvider)


    }

    /*  AndroidView(
          factory = { ctx ->
              androidx.camera.view.PreviewView(ctx).apply {
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
      }*/
}

