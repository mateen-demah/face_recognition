package com.mateendemah.face_recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mateendemah.face_recognition.composables.MessagesBanner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.properties.Delegates

class RecognitionUI : ComponentActivity() {

    private val intentResult = Intent()
    private lateinit var recMode: String // recognition mode

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)

    private lateinit var faceEmbeddings: List<String>
    private var existingFaces: List<Face>? = null
    private var similarityThreshold by Delegates.notNull<Float>()

    override fun onStart() {
        super.onStart()

        val mode = intent.getStringExtra(MODE)
        val faceEmbedding = intent.getStringExtra(FACE_STRING)
        val faceEmbeddings = intent.getStringArrayListExtra(FACE_STRINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.existingFaces =
                intent.getParcelableArrayListExtra(EXISTING_FACES, Face::class.java)?.toList()
        } else {
            this.existingFaces = intent.getParcelableArrayListExtra(EXISTING_FACES)
        }

        Log.e(
            "[intent extras]",
            "mode: $mode, faceEmbedding: $faceEmbedding, faces: $faceEmbeddings"
        )

        if (mode == ENROLL_MODE && faceEmbeddings == null) {
            intentResult.putExtra(
                ERROR_400,
                "face strings are required to detect duplicates. It could be an empty string"
            )
            closeActivity()
        } else if (mode == VERIFY_MODE && faceEmbedding == null) {
            intentResult.putExtra(
                ERROR_400,
                "A face string is required to do verification. Need to compare what's detected to something"
            )
            closeActivity()
        }

        requestCameraPermission()

        this.faceEmbeddings = faceEmbeddings ?: emptyList()
        this.similarityThreshold = intent.getFloatExtra(SIMILARITY_THRESHOLD, 0.65f)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        mode?.let {
            recMode = mode
            setContent {
                RecognitionUi(
                    mode = mode.toRecognitionMode(),
                    faceEmbedding = faceEmbedding,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalCacheDir

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    @Composable
    fun RecognitionUi(
        mode: RecognitionMode,
        faceEmbedding: String? = null,
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        var overlaySize by remember { mutableStateOf(Size.Zero) }
        var imgSize by remember { mutableStateOf(0 to 0) }

        var boundingBoxes by remember { mutableStateOf<List<Rect>>(emptyList()) }

        val errorMessage = remember { mutableStateOf("") }
        val detectedEmbedding = remember { mutableStateOf<Array<FloatArray>?>(null) }

        val recognitionState = remember { mutableStateOf(RecognitionState.SEARCHING_FACE) }
        val recognitionStarted = remember { mutableStateOf(mode == RecognitionMode.VERIFY) }
        val enrollmentImageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
        val imageUri = remember { mutableStateOf<Uri?>(null) }

        val faceRecogniser = remember(mode) {
            FaceRecogniser(
                context = context,
                mode = mode,
                faceEmbedding = faceEmbedding ?: "",
                drawFaceBoxes = { faces, _imgSize ->
                    boundingBoxes = faces
                    imgSize = _imgSize.first to _imgSize.second
                    if (boundingBoxes.isEmpty()) {
                        recognitionState.value = RecognitionState.SEARCHING_FACE
                    }
                },
                onFaceDetected = {
                    recognitionState.value = RecognitionState.FACE_DETECTED
                },
                onFaceRecognised = {
                    recognitionStarted.value = false
                    recognitionState.value = RecognitionState.RECOGNISED
                    detectedEmbedding.value = it
                    takePhoto(
                        filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
                        imageCapture = enrollmentImageCapture,
                        outputDirectory = outputDirectory,
                        executor = cameraExecutor,
                        onImageCaptured = { it1 ->
                            imageUri.value = it1
                        },
                        onError = { it1 ->
                            Log.d(TAG, "=============> Checkpoint onError $it1<==============")
                        },
                    )
                },
                onErrorDetected = {
                    recognitionState.value = RecognitionState.SHOW_ERROR_MESSAGE
                    errorMessage.value = it
                },
                onVerificationComplete = {
                    recognitionState.value =
                        if (it) RecognitionState.VERIFIED_SUCCESSFULLY else RecognitionState.VERIFICATION_FAILED
                },
                shouldDoRecognition = { recognitionStarted.value },
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

        val negativeVerificationOverturned = remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = mode == RecognitionMode.VERIFY,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column {
                Text(
                    stringResource(id = R.string.take_a_picture_of_the_farmer_full_face),
                    color = Color(0xFF485465),
                    modifier = Modifier
                        .padding(vertical = 18.dp)
                        .padding(bottom = 6.dp),
                    fontSize = 12.sp,
                )

                Box {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)

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

                            previewView
                        },
                        modifier = Modifier.onSizeChanged {
                            overlaySize = it.toSize()
//                            Log.d("=> Size")
                        },
                        update = {
                            overlaySize = Size(
                                it.width.toFloat(),
                                it.height.toFloat(),
                            )
                        }
                    )

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

                    Box(Modifier.zIndex(2f)) {
                        Text(
                            when (recognitionState.value) {
                                RecognitionState.VERIFICATION_FAILED -> stringResource(id = R.string.face_verification_doesnt_match_message)
                                else -> stringResource(id = R.string.make_sure_the_farmer_is_at_center)
                            },
                            color = when (recognitionState.value) {
                                RecognitionState.VERIFICATION_FAILED -> Color(0xFFFF4B55)
                                else -> Color(0xFF0F0F37)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = (overlaySize.height * 0.8).dp)
                                .padding(bottom = 6.dp),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.fillMaxHeight(0.5f)) {
                    Text(
                        when (recognitionState.value) {
                            RecognitionState.VERIFICATION_FAILED -> stringResource(id = R.string.face_verification_doesnt_match_message)
                            else -> stringResource(id = R.string.make_sure_the_farmer_is_at_center)
                        },
                        color = when (recognitionState.value) {
                            RecognitionState.VERIFICATION_FAILED -> Color(0xFFFF4B55)
                            else -> Color(0xFF0F0F37)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = (overlaySize.height * 0.15).dp),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.weight(1f))

                    if (recognitionState.value == RecognitionState.VERIFICATION_FAILED) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                modifier = Modifier.size(24.dp),
                                checked = negativeVerificationOverturned.value,
                                onCheckedChange = {
                                    negativeVerificationOverturned.value =
                                        !negativeVerificationOverturned.value
                                },
                                colors = CheckboxDefaults.colors(
                                    checkmarkColor = Color.White,
                                    checkedColor = colorResource(id = R.color.green_500),
                                    uncheckedColor = Color(0xFFE8E8E8),
                                ),
                            )
                            Text(
                                stringResource(id = R.string.i_can_confirm_that_this_is_the_image_of_the_farmer_selected),
                                color = Color(0xFF7D8CA3),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        enabled = recognitionState.value == RecognitionState.VERIFIED_SUCCESSFULLY || negativeVerificationOverturned.value,
                        onClick = {
                            onFaceVerificationComplete(
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorResource(
                                id = R.color.green_500
                            ), contentColor = Color.White
                        ),
                    ) {
                        Text(stringResource(id = R.string.confirm))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mode == RecognitionMode.ENROLL,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.background(Color.White)
        ) {
            Column {
                MessagesBanner(
                    errorMessage = errorMessage,
                    showBanner = recognitionStarted,
                    recognitionState = recognitionState,
                )
                Box {
                    CameraView(
                        executor = cameraExecutor,
                        imageCapture = enrollmentImageCapture,
                        faceDetector = faceRecogniser,
                        modifier = Modifier,
                        update = {
                            overlaySize = Size(
                                it.width.toFloat(),
                                it.height.toFloat(),
                            )
                        },
                        shouldStartFrameProcessing = recognitionStarted,
                        imageUri = imageUri,
                        saveAndClose = {
                            val detectedFace = Embedding.embeddingStringFromJavaObject(
                                detectedEmbedding.value!!
                            ).embedding
                            val similarFaces = FaceRecogniser.faceExists(
                                faceList = existingFaces?.toList() ?: emptyList(),
                                detectedFace = detectedEmbedding.value!!,
                                similarityThreshold,
                            )
                            Log.d("============> checkpoint similar faces", "$similarFaces")
                            Log.d("============> checkpoint detected face", detectedFace)
                            onFaceRecognised(
                                faceString = detectedFace,
                                similarFaces = similarFaces,
                                imagePath = imageUri.value?.path ?: ""
                            )
                        }
                    )

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
        }

        BackHandler {
            Log.d("got here", "back handler")
            closeActivity(activityResult = Activity.RESULT_CANCELED)
        }
    }

    private fun closeActivity(activityResult: Int = Activity.RESULT_OK) {
        if (this::recMode.isInitialized) {
            intentResult.putExtra(MODE, recMode)
        }
        setResult(activityResult, intentResult)
        finish()
    }

    private fun onFaceRecognised(
        faceString: String,
        similarFaces: List<Face>,
        imagePath: String = "",
    ) {
        intentResult.putExtra(FACE_STRING, faceString)
        intentResult.putExtra(IMAGE_PATH, imagePath)
        intentResult.putParcelableArrayListExtra(SIMILAR_FACES, ArrayList(similarFaces))
        closeActivity()
    }

    private fun onFaceVerificationComplete() {
        intentResult.putExtra(VERIFICATION_SUCCESSFUL, true)
        closeActivity()
    }


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("kilo", "Permission granted")
            shouldShowCamera.value = true
        } else {
            Log.i("kilo", "Permission denied")
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                shouldShowCamera.value = true
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> shouldShowCamera.value = false

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}