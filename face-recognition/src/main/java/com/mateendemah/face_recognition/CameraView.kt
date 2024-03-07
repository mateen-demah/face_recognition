package com.mateendemah.face_recognition

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.mateendemah.face_recognition.composables.EnrollButtons
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraView(
    executor: Executor,
    imageCapture: ImageCapture,
    faceDetector: FaceRecogniser,
    modifier: Modifier,
    update: (PreviewView) -> Unit,
    shouldStartFrameProcessing: MutableState<Boolean>,
    imageUri: MutableState<Uri?>,
    saveAndClose: () -> Unit,
) {
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    val faceDetectionUseCase = ImageAnalysis.Builder().setTargetResolution(
        android.util.Size(
            previewView.width,
            previewView.height
        )
    ).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
        setAnalyzer(executor, faceDetector)
    }

    LaunchedEffect(lensFacing, faceDetectionUseCase) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            faceDetectionUseCase,
        )
        preview.setSurfaceProvider(previewView.surfaceProvider)
        update(previewView)
    }

    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
        if (imageUri.value == null) {
            AndroidView(
                { previewView },
                modifier = modifier.then(Modifier.fillMaxSize()),
                update = update,
            )
        } else {
            Image(
                painter = rememberAsyncImagePainter(imageUri.value!!),
                modifier = Modifier.fillMaxSize(),
                contentDescription = "image for recognition",
                contentScale = ContentScale.Crop,
            )
        }
        EnrollButtons(
            scanning = shouldStartFrameProcessing, img = imageUri,
            modifier =
            Modifier.align(alignment = Alignment.BottomCenter),
            saveScan = saveAndClose,
        )
    }
}

fun takePhoto(
    filenameFormat: String,
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onError: (ImageCaptureException) -> Unit,
    onImageCaptured: (Uri) -> Unit,
) {
    val photoFile = File(
        outputDirectory,
        SimpleDateFormat(filenameFormat, Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions, executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e("kilo", "Take photo error:", exception)
                onError(exception)
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri)
            }
        },
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }