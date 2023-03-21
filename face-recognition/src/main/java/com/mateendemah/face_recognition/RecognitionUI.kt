package com.mateendemah.face_recognition

import android.graphics.Rect
import android.graphics.RectF
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

@Composable
fun RecognitionUi(
    mode: RecognitionMode,
    visible: MutableState<Boolean>,
    faceEmbedding: String? = null,
    onFaceRecognised: ((String) -> Unit)? = null,
    onFaceVerificationComplete: ((successful: Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var overlaySize by remember { mutableStateOf(Size.Zero) }
    var imgSize by remember { mutableStateOf(0 to 0) }

    var boundingBoxes by remember { mutableStateOf<List<Rect>>(emptyList()) }

    var images by remember { mutableStateOf<List<DisplayImage>?>(null) }

    val errorMessage = remember { mutableStateOf("") }
    val detectedEmbedding = remember { mutableStateOf(Embedding("")) }

    val recognitionState = remember { mutableStateOf(RecognitionState.SEARCHING_FACE) }

    val faceRecogniser = remember(mode) {
        FaceRecogniser(
            context = context,
            mode = mode,
            faceEmbedding = faceEmbedding ?: "",
            drawFaceBoxes = { faces, _imgSize ->
                boundingBoxes = faces
                imgSize = _imgSize.first to _imgSize.second
            },
            onCapture = { dispImages ->
                images = dispImages
            },
            onFaceDetected = {
                recognitionState.value = RecognitionState.FACE_DETECTED
            },
            onFaceRecognised = {
                recognitionState.value = RecognitionState.RECOGNISED
                detectedEmbedding.value = it
            },
            onErrorDetected = {
                recognitionState.value = RecognitionState.SHOW_ERROR_MESSAGE
                errorMessage.value = it
            },
            onVerificationComplete = {
                recognitionState.value =
                    if (it) RecognitionState.VERIFIED_SUCCESSFULLY else RecognitionState.VERIFICATION_FAILED
            },
        )
    }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(
                Surface.ROTATION_0
            ).setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
    }

    val lensFacing = remember {
        mutableStateOf(
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
        )
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        when (mode) {
                            RecognitionMode.ENROLL -> when (recognitionState.value) {
                                RecognitionState.RECOGNISED -> colorResource(id = R.color.green_500)
                                else -> Color.White
                            }
                            RecognitionMode.VERIFY -> when (recognitionState.value) {
                                RecognitionState.VERIFIED_SUCCESSFULLY -> colorResource(id = R.color.green_500)
                                RecognitionState.VERIFICATION_FAILED -> colorResource(id = R.color.till_red)
                                else -> Color.White
                            }
                        }
                    )
                    .padding(vertical = 16.dp), contentAlignment = Alignment.Center
            ) {
                when (recognitionState.value) {
                    RecognitionState.SEARCHING_FACE -> Text(stringResource(R.string.searching_for_face))
                    RecognitionState.FACE_DETECTED -> Text(stringResource(R.string.faceDetected))
                    RecognitionState.RECOGNISING -> Text(stringResource(id = R.string.running_recognition))
                    RecognitionState.RECOGNISED -> Text(stringResource(id = R.string.face_recognition_successful))
                    RecognitionState.VERIFIED_SUCCESSFULLY -> Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp
                        )
                    ) {
                        Text(stringResource(R.string.checkmark))
                        Text(stringResource(R.string.successful_verification))
                    }
                    RecognitionState.VERIFICATION_FAILED -> Text(stringResource(id = R.string.match_failed))
                    else -> Text(errorMessage.value, color = colorResource(R.color.till_red))
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
//                            previewView.controller = cameraController

                            val executor = ContextCompat.getMainExecutor(ctx)
                            val faceRecognitionUseCase =
                                ImageAnalysis.Builder().setTargetResolution(
                                    android.util.Size(
                                        previewView.width,
                                        previewView.height
                                    )
                                ).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build().apply {
                                        setAnalyzer(executor, faceRecogniser)
                                    }
                            if (visible.value) {
//                                startCamera(cameraProviderFuture = cameraProviderFuture, previewView = previewView, cameraSelector = lensFacing.value, lifecycleOwner = lifecycleOwner, imageCapture = imageCapture, useCase = faceRecognitionUseCase, executor = executor)
                                cameraProviderFuture.addListener(
                                    {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val cameraSelector = lensFacing.value

                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            imageCapture,
                                            faceRecognitionUseCase,
                                            preview
                                        )
                                    },
                                    executor,
                                )
                            }
                            previewView
                        },
                        modifier = Modifier.onSizeChanged { overlaySize = it.toSize() },
                        update = {
                            overlaySize = Size(
                                it.width.toFloat(),
                                it.height.toFloat(),
                            )
                        }
                    )

                    images?.let {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(images!!.size) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        bitmap = images!![it].image.asImageBitmap(),
                                        contentDescription = images!![it].description
                                    )
                                    Text(images!![it].description)
                                }
                            }
                        }
                    }
                }

                Canvas(
                    modifier =
                    Modifier
                        .height(overlaySize.height.dp)
                        .width(overlaySize.width.dp),
                ) {
                    for (box in boundingBoxes) {
                        val rect = box.transformToRectF(
                            imgSize = imgSize,
                            previewHeight = overlaySize.height,
                            previewWidth = overlaySize.width,
                            lensFacing = lensFacing.value.lensFacing!!,
                        )

                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width(), rect.height()),
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp), contentAlignment = Alignment.CenterEnd
                ) {
                    Button(contentPadding = PaddingValues(16.dp), onClick = {
                        lensFacing.value = if (lensFacing.value.lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build() else CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build()
                    }) {
                        Text(stringResource(id = R.string.switch_camera))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(
                                        color = colorResource(id = R.color.green_500),
                                        shape = CircleShape,
                                    ),
                            ) {
                                Text(
                                    "XX",
                                    color = MaterialTheme.colors.onPrimary,
                                    modifier = Modifier.align(Alignment.Center),
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                            Column {
                                Text("Sample Name", fontWeight = FontWeight.Bold)
                                Text("0540000000")
                            }
                        }

                        when (recognitionState.value) {
                            RecognitionState.VERIFICATION_FAILED -> Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    16.dp
                                )
                            ) {
                                Button(
                                    onClick = { visible.value = false },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(vertical = 16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = colorResource(
                                            id = R.color.green_500
                                        ), contentColor = Color.White
                                    )
                                ) {
                                    Text(stringResource(id = R.string.close))
                                }
                                Button(
                                    onClick = { /*TODO*/ },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(vertical = 16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = colorResource(
                                            id = R.color.green_500
                                        ), contentColor = Color.White
                                    )
                                ) {
                                    Text(stringResource(id = R.string.retry))
                                }
                            }
                            else -> Button(
                                enabled = when (mode) {
                                    RecognitionMode.ENROLL -> recognitionState.value == RecognitionState.RECOGNISED
                                    RecognitionMode.VERIFY -> recognitionState.value == RecognitionState.VERIFIED_SUCCESSFULLY
                                },
                                onClick = {
                                    when (mode) {
                                        RecognitionMode.ENROLL -> onFaceRecognised?.invoke(
                                            detectedEmbedding.value.embedding
                                        )
                                        RecognitionMode.VERIFY -> {
                                            onFaceVerificationComplete?.invoke(
                                                recognitionState.value == RecognitionState.VERIFIED_SUCCESSFULLY
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = colorResource(
                                        id = R.color.green_500
                                    ), contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = when (mode) {
                                        RecognitionMode.ENROLL -> stringResource(id = R.string.enroll)
                                        RecognitionMode.VERIFY -> stringResource(id = R.string.proceed)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    BackHandler {
        visible.value = false
    }
}

fun Rect.transformToRectF(
    imgSize: Pair<Int, Int>,
    previewWidth: Float,
    previewHeight: Float,
    lensFacing: Int,
): RectF {
    val width = imgSize.first.toFloat()
    val height = imgSize.second.toFloat()

    val scaleX = previewWidth / width
    val scaleY = previewHeight / height

    val left = if (lensFacing == CameraSelector.LENS_FACING_FRONT) (width - left).toInt() else left
    val right = if (lensFacing == CameraSelector.LENS_FACING_FRONT) (width - right).toInt() else right

    val scaledLeft = scaleX * left
    val scaledTop = scaleY * top
    val scaledRight = scaleX * right
    val scaledBottom = scaleY * bottom
    return RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)
}