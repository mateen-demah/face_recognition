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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraView(
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    faceDetector: FaceDetector,
    modifier: Modifier,
    update: (PreviewView) -> Unit,
) {
    // 1
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    val imageCaptured = remember { mutableStateOf(false) }
    val imageUri = remember { mutableStateOf<Uri?>(null)}

    val faceDetectionUseCase = ImageAnalysis.Builder().setTargetResolution(
            android.util.Size(
                previewView.width,
                previewView.height
            )
        ).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(executor, faceDetector)
            }

    // 2
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

    // 3
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
        if (!imageCaptured.value){
            AndroidView({ previewView }, modifier = modifier.then(Modifier.fillMaxSize(),), update = update,)
        } else {
            Image(painter = rememberAsyncImagePainter(imageUri.value!!), modifier = Modifier.fillMaxSize(), contentDescription = "image for recognition", contentScale = ContentScale.FillBounds,)
        }

        Box(
            modifier = Modifier
                .align(alignment = Alignment.BottomCenter)
                .fillMaxHeight(0.15f)
                .fillMaxWidth()
                .background(color = Color.Black.copy(alpha = 0.37f)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                modifier = Modifier.align(alignment = Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = imageCaptured.value) {
                    IconButton(onClick = { imageCaptured.value = false
                        imageUri.value?.path?.let { File(it).delete() }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Cancel,
                            contentDescription = "cancel",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(
                            color = when (imageCaptured.value) {
                                false -> Color.White
                                true -> Color.Gray
                            },
                            shape = CircleShape,
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                        ) {
                            takePhoto(
                                filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
                                imageCapture = imageCapture,
                                outputDirectory = outputDirectory,
                                executor = executor,
                                onImageCaptured = {
                                    imageUri.value = it
                                    imageCaptured.value = true
                                },
                                onError = onError
                            )
                        },
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(color = Color(235, 82, 80, 255), shape = CircleShape)
                            .shadow(
                                elevation = 4.dp,
                                spotColor = Color(0x4226499A),
                                ambientColor = Color(0x4226499A),
                            )
                            .align(Alignment.Center),
                    )
                }

                AnimatedVisibility(visible = imageCaptured.value) {
                    IconButton(onClick = {
                        imageUri.value?.let { onImageCaptured(it) }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "cancel",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
//            IconButton(
//                modifier = Modifier.padding(bottom = 20.dp),
//                onClick = {
//                    Log.i("kilo", "ON CLICK")
//                    takePhoto(
//                        filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
//                        imageCapture = imageCapture,
//                        outputDirectory = outputDirectory,
//                        executor = executor,
//                        onImageCaptured = onImageCaptured,
//                        onError = onError
//                    )
//                },
//                content = {
//                    Icon(
//                        imageVector = Icons.Sharp.Lens,
//                        contentDescription = "Take picture",
//                        tint = Color.White,
//                        modifier = Modifier
//                            .size(100.dp)
//                            .padding(1.dp)
//                            .border(1.dp, Color.White, CircleShape)
//                    )
//                }
//            )
        }
    }
}

private fun takePhoto(
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

    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onError(exception: ImageCaptureException) {
            Log.e("kilo", "Take photo error:", exception)
            onError(exception)
        }

        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            val savedUri = Uri.fromFile(photoFile)
            onImageCaptured(savedUri)
        }
    })
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }