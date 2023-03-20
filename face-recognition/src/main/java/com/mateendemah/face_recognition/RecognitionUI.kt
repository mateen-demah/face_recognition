package com.mateendemah.face_recognition

import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

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
    val boundingBoxColor = remember { mutableStateOf(Color.Red) }

    val captureClicked = remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<DisplayImage>?>(null) }
    var recResult by remember { mutableStateOf<Embedding?>(null)}
    var errorMessage by remember { mutableStateOf<String?>(null)}
    val extras = remember { mutableStateListOf<String>() }

    val faceRecogniser = remember {
        FaceRecogniser(
            context = context,
            capture = captureClicked,
            boundingBoxColor = boundingBoxColor,
            mode = mode,
            extras = extras,
            faceEmbedding = faceEmbedding?:"",
            drawFaceBoxes = { faces, _imgSize ->
                boundingBoxes = faces
                imgSize = _imgSize.first to _imgSize.second
            },
            onCapture = { dispImages, result ->
                captureClicked.value = false
                images = dispImages
                recResult = result
            },
            onErrorDetected = {
                captureClicked.value = false
                errorMessage = it
            },
            onRecognitionComplete = onFaceVerificationComplete,
        )
    }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(
                Surface.ROTATION_0
            ).setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
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
                            )
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().apply {
                                    setAnalyzer(executor, faceRecogniser)
                                }
                        if (visible.value) {
                            cameraProviderFuture.addListener(
                                {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    // update overlaySize
//                                    overlaySize =

                                    val cameraSelector = CameraSelector.Builder()
                                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                        .build()

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        imageCapture,
                                        faceRecognitionUseCase,
                                        preview
                                    )

                                    // update previewSize
//                                                previewSize = Size(
//                                                    previewView.width.toFloat(), // * previewView.scaleX,
//                                                    previewView.height.toFloat() // * previewView.scaleY
//                                                )
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

                errorMessage?.let{
                    Box(Modifier.fillMaxWidth()) {
                        Text(text = errorMessage!!, color = Color.Red)
                    }
                }

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Row (horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically){
                        if(mode == RecognitionMode.VERIFY){
                            recResult?.let {
//                                Text("CONFIDENCE: ${recResult!!.confidence}")
                            }
                        }
                        Button(
                            enabled = recResult == null,
                            onClick = {
                                extras.clear()
                                captureClicked.value = true
                                recResult = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Text(text = stringResource(id = R.string.capture))
                        }

                        Button(
                            enabled = recResult != null,
                            onClick = {
                                when (mode){ RecognitionMode.ENROLL -> onFaceRecognised?.invoke(recResult!!.embedding)
                                RecognitionMode.VERIFY -> onFaceVerificationComplete?.invoke(sameFace(faceEmbedding!!, recResult!!.embeddingStringToJavaObject().first()))}
                                recResult = null
                                visible.value = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Text(text = stringResource(id = R.string.done))
                        }
                    }
                }

                images?.let{
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(images!!.size){
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally){
                                Image(bitmap = images!![it].image.asImageBitmap(), contentDescription = images!![it].description)
                                Text(images!![it].description)
                            }
                        }
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
//                    item{
//                        recResult?.let {
//                            Text(text = "EMBEDDING: ${recResult!!.embedding}")
//                            Log.d("EMBEDDING STRING", recResult!!.embedding)
//                            Log.d("EMBEDDING LIST", "${recResult!!.embedding.toList()}")
//                        }
//                    }

                    items(extras.size){
                        Text(extras[it])
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
                        previewWidth = overlaySize.width
                    )
                    drawRoundRect(
                        color = boundingBoxColor.value,
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width(), rect.height()),
                        cornerRadius = CornerRadius(16f, 16f),
                        style = Stroke(width = 10f),
                    )
                }
            }
        }
    }

    BackHandler {
        visible.value = false
    }
}

fun Rect.transformToRectF(imgSize: Pair<Int, Int>, previewWidth: Float, previewHeight: Float): RectF {
    val width = imgSize.first.toFloat()
    val height = imgSize.second.toFloat()

    val scaleX = previewWidth / width
    val scaleY = previewHeight / height

    val left = width - left
    val right = width - right

    val scaledLeft = scaleX * left
    val scaledTop = scaleY * top
    val scaledRight = scaleX * right
    val scaledBottom = scaleY * bottom
    return RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)
}

private fun sameFace(given: String, detected: FloatArray): Boolean{
    val embeddingArray1 = Embedding(given).embeddingStringToJavaObject().first()
    var distance = 0f
    for (index in embeddingArray1.indices){
        val difference = embeddingArray1[index] - detected[index]
        distance += difference * difference
    }
    distance = sqrt(distance.toDouble()).toFloat()
    Log.d("VERIFICATION CONFIDENCE", "$distance")
    return distance < 1.0f
}