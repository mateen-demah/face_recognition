package com.mateendemah.face_recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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

    private lateinit var photoUri: Uri
//    private var shouldShowPhoto: MutableState<Boolean> = mutableStateOf(false)

    private lateinit var faceEmbeddings: List<String>
    private var faces: List<Face>? = null
    private var similarityThreshold by Delegates.notNull<Float>()

    override fun onStart() {
        super.onStart()

        val mode = intent.getStringExtra(MODE)
        val faceEmbedding = intent.getStringExtra(FACE_STRING)
        val faceEmbeddings = intent.getStringArrayListExtra(FACE_STRINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            this.faces = intent.getParcelableArrayListExtra(EXISTING_FACES, Face::class.java)?.toList()
        } else {
            this.faces = intent.getParcelableArrayListExtra(EXISTING_FACES)
        }

        // subject of recognition
        val subjectName = intent.getStringExtra(SUBJECT_NAME)
        val subjectContact = intent.getStringExtra(SUBJECT_CONTACT)
        val subjectImageUri = intent.getStringExtra(SUBJECT_IMAGE_URI)

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

        this.faceEmbeddings = faceEmbeddings?: emptyList()
        this.similarityThreshold = intent.getFloatExtra(SIMILARITY_THRESHOLD, 0.65f)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        mode?.let {
            recMode = mode
            setContent {
                RecognitionUi(
                    mode = mode.toRecognitionMode(),
                    faceEmbedding = faceEmbedding,
                    faceEmbeddings = faceEmbeddings ?: emptyList(),
                    subjectName = subjectName,
                    subjectContact = subjectContact,
                    subjectImageUri = subjectImageUri,
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
        faceEmbeddings: List<String>,
        subjectName: String?,
        subjectContact: String?,
        subjectImageUri: String?,
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
                faceEmbeddings = faceEmbeddings,
                drawFaceBoxes = { faces, _imgSize ->
                    boundingBoxes = faces
                    imgSize = _imgSize.first to _imgSize.second
                    if (boundingBoxes.isEmpty()) {
                        recognitionState.value = RecognitionState.SEARCHING_FACE
                    }
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



        val errorMsg = remember { mutableStateOf<String?>(null) }
        val showError = remember(errorMsg){ errorMsg.value != null }
        val faceDetector = remember {
            FaceDetector(
                drawFaceBoxes = { faces, _imgSize ->
                    boundingBoxes = faces
                    imgSize = _imgSize.first to _imgSize.second
                    if (boundingBoxes.isEmpty()) {
                        recognitionState.value = RecognitionState.SEARCHING_FACE
                    }
                },
                onErrorDetected = {
                    errorMsg.value = it
                },
                onFaceDetected = {
                    recognitionState.value = RecognitionState.FACE_DETECTED
                }
            )
        }
        val recogniser = remember{
            TFLiteObjectDetectionAPIModel.create(
                context.assets,
                MODEL_FILENAME,
                LABELS_FILENAME,
                MODEL_INPUT_SIZE,
                MODEL_IS_QUANTIZED
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

        var negativeVerificationOverturned = remember{mutableStateOf(false)}
        AnimatedVisibility(
            visible = mode == RecognitionMode.VERIFY,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column {
//                Box(
//                    Modifier
//                        .fillMaxWidth()
//                        .background(
//                            when (mode) {
//                                RecognitionMode.ENROLL -> when (recognitionState.value) {
//                                    RecognitionState.RECOGNISED -> colorResource(id = R.color.green_500)
//                                    else -> Color.White
//                                }
//
//                                RecognitionMode.VERIFY -> when (recognitionState.value) {
//                                    RecognitionState.VERIFIED_SUCCESSFULLY -> colorResource(id = R.color.green_500)
//                                    RecognitionState.VERIFICATION_FAILED -> colorResource(id = R.color.till_red)
//                                    else -> Color.White
//                                }
//                            }
//                        )
//                        .padding(vertical = 16.dp), contentAlignment = Alignment.Center
//                ) {
//                    when (recognitionState.value) {
//                        RecognitionState.SEARCHING_FACE -> Text(
//                            stringResource(R.string.searching_for_face),
//                            color = Color.Black
//                        )
//                        RecognitionState.FACE_DETECTED -> Text(
//                            stringResource(R.string.faceDetected),
//                            color = Color.Black
//                        )
//                        RecognitionState.RECOGNISING -> Text(
//                            stringResource(id = R.string.running_recognition),
//                            color = Color.Black
//                        )
//                        RecognitionState.RECOGNISED -> Text(
//                            stringResource(id = R.string.face_recognition_successful),
//                            color = when (mode) {
//                                RecognitionMode.ENROLL -> Color.White
//                                RecognitionMode.VERIFY -> Color.Black
//                            }
//                        )
//                        RecognitionState.VERIFIED_SUCCESSFULLY -> Row(
//                            horizontalArrangement = Arrangement.spacedBy(
//                                16.dp
//                            )
//                        ) {
//                            Text(stringResource(R.string.checkmark))
//                            Text(stringResource(R.string.successful_verification))
//                        }
//                        RecognitionState.VERIFICATION_FAILED -> Text(stringResource(id = R.string.match_failed))
//                        else -> Text(errorMessage.value, color = colorResource(R.color.till_red))
//                    }
//                }

                Text(stringResource(id = R.string.take_a_picture_of_the_farmer_full_face), color = Color(0xFF485465), modifier = Modifier
                    .padding(vertical = 18.dp)
                    .padding(bottom = 6.dp), fontSize = 12.sp,)

                Box{
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
                            modifier = Modifier.onSizeChanged { overlaySize = it.toSize()
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
//            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
//                Column {
//                    // switch camera button
//                    // todo: implement camera switching to allow user use front facing camera
////                    Box(
////                        Modifier
////                            .fillMaxWidth()
////                            .padding(16.dp), contentAlignment = Alignment.CenterEnd
////                    ) {
////                        Button(contentPadding = PaddingValues(16.dp), onClick = {
////                            lensFacing.value =
////                                if (lensFacing.value.lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.Builder()
////                                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
////                                    .build() else CameraSelector.Builder()
////                                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
////                                    .build()
////                        }) {
////                            Text(stringResource(id = R.string.switch_camera))
////                        }
////                    }
//                    Box(
//                        Modifier
//                            .fillMaxWidth()
//                            .background(Color.White)
//                            .padding(16.dp)
//                    ) {
//                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
//                            Row(
//                                horizontalArrangement = Arrangement.spacedBy(16.dp),
//                                verticalAlignment = Alignment.CenterVertically
//                            ) {
//                                when (subjectImageUri.isNullOrBlank()) {
//                                    true -> Box(
//                                        modifier = Modifier
//                                            .size(60.dp)
//                                            .background(
//                                                color = colorResource(id = R.color.green_500),
//                                                shape = CircleShape,
//                                            ),
//                                    ) {
//                                        Text(
//                                            when (subjectName.isNullOrBlank()) {
//                                                true -> "XX"
//                                                false -> subjectName.split(" ")
//                                                    .joinToString { it.first().uppercase() }
//                                            },
//                                            color = MaterialTheme.colors.onPrimary,
//                                            modifier = Modifier.align(Alignment.Center),
//                                            overflow = TextOverflow.Ellipsis,
//                                            textAlign = TextAlign.Center,
//                                            maxLines = 1,
//                                        )
//                                    }
//                                    false -> AsyncImage(
//                                        model = subjectImageUri,
//                                        contentDescription = null,
//                                        contentScale = ContentScale.Crop,
//                                        modifier = Modifier
//                                            .size(60.dp)
//                                            .clip(CircleShape)
//                                    )
//                                }
//                                Column {
//                                    Text(
//                                        subjectName ?: "Name not found",
//                                        fontWeight = FontWeight.Bold
//                                    )
//                                    Text(subjectContact ?: "054xxxxxxx")
//                                }
//                            }
//
//                            when (recognitionState.value) {
//                                RecognitionState.VERIFICATION_FAILED -> Row(
//                                    horizontalArrangement = Arrangement.spacedBy(
//                                        16.dp
//                                    )
//                                ) {
//                                    Button(
//                                        onClick = { closeActivity() },
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            .weight(1f),
//                                        contentPadding = PaddingValues(vertical = 16.dp),
//                                        colors = ButtonDefaults.buttonColors(
//                                            backgroundColor = colorResource(
//                                                id = R.color.green_500
//                                            ), contentColor = Color.White
//                                        )
//                                    ) {
//                                        Text(stringResource(id = R.string.close))
//                                    }
//                                    Button(
//                                        onClick = { /*TODO*/ },
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            .weight(1f),
//                                        contentPadding = PaddingValues(vertical = 16.dp),
//                                        colors = ButtonDefaults.buttonColors(
//                                            backgroundColor = colorResource(
//                                                id = R.color.green_500
//                                            ), contentColor = Color.White
//                                        )
//                                    ) {
//                                        Text(stringResource(id = R.string.retry))
//                                    }
//                                }
//                                else -> Button(
//                                    enabled = when (mode) {
//                                        RecognitionMode.ENROLL -> recognitionState.value == RecognitionState.RECOGNISED
//                                        RecognitionMode.VERIFY -> recognitionState.value == RecognitionState.VERIFIED_SUCCESSFULLY
//                                    },
//                                    onClick = {
//                                        when (mode) {
//                                            RecognitionMode.ENROLL -> onFaceRecognised(
//                                                detectedEmbedding.value.embedding,
//                                                emptyList(),
//                                            )
//                                            RecognitionMode.VERIFY -> {
//                                                onFaceVerificationComplete(
//                                                    recognitionState.value == RecognitionState.VERIFIED_SUCCESSFULLY
//                                                )
//                                            }
//                                        }
//                                    },
//                                    modifier = Modifier.fillMaxWidth(),
//                                    contentPadding = PaddingValues(vertical = 16.dp),
//                                    colors = ButtonDefaults.buttonColors(
//                                        backgroundColor = colorResource(
//                                            id = R.color.green_500
//                                        ), contentColor = Color.White
//                                    )
//                                ) {
//                                    Text(
//                                        text = when (mode) {
//                                            RecognitionMode.ENROLL -> stringResource(id = R.string.enroll)
//                                            RecognitionMode.VERIFY -> stringResource(id = R.string.proceed)
//                                        }
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter){
                Column(Modifier.fillMaxHeight(0.5f)){
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
                            .padding(top = (overlaySize.height * 0.1).dp),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.weight(1f))

                    if(recognitionState.value == RecognitionState.VERIFICATION_FAILED){
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
                                    checkedColor = MaterialTheme.colors.primary,
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
                                        true
                                    )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorResource(
                                id = R.color.green_500
                            ), contentColor = Color.White
                        ),){
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
        ){
            Box {
                Column {
                    AnimatedVisibility(visible = showError) {
                        errorMsg.value?.let {
                            Text(
                                it, color = Color.Red, modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .align(alignment = Alignment.CenterHorizontally)
                            )
                        }
                    }
                    CameraView(
                        outputDirectory = outputDirectory,
                        executor = cameraExecutor,
                        onImageCaptured = { handleImageCapture(it, recogniser) },
                        onError = {},
                        faceDetector = faceDetector,
                        modifier = Modifier, //.onSizeChanged { overlaySize = it.toSize() },
                        update = {
                            overlaySize = Size(
                                it.width.toFloat(),
                                it.height.toFloat(),
                            )
                        },
                    )
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
        
        BackHandler {
            Log.d("got here", "back handler")
            closeActivity(activityResult = Activity.RESULT_CANCELED)
        }
    }

    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
    private val detector = FaceDetection.getClient(highAccuracyOpts)
    private fun handleImageCapture(uri: Uri, recogniser: SimilarityClassifier){
        // load image as bitmap
        val imgBmp = BitmapFactory.decodeFile(uri.path)
        var detectedFace: Rect? = null

        // attempt face detection
        val inputImage = InputImage.fromBitmap(imgBmp, 0)
        detector.process(inputImage).addOnSuccessListener {
            if (it.isNotEmpty()) {
                detectedFace = it.first().boundingBox
            }

            // attempt recognition
            detectedFace?.let { bounds ->
                try {
                    val faceBitmap = Bitmap.createBitmap(
                        imgBmp,
                        bounds.left,
                        bounds.top,
                        bounds.width(),
                        bounds.height(),
                    )
                    val scaledFaceBitmap = Bitmap.createScaledBitmap(
                        faceBitmap,
                        MODEL_INPUT_SIZE,
                        MODEL_INPUT_SIZE,
                        true
                    )

                    val recognitionResult = recogniser.recognizeImage(
                        scaledFaceBitmap,
                        true
                    )
                    val embedding = recognitionResult.first().extra
                    if (recognitionResult.isNotEmpty()) {
                        val faceEmbedding =
                            Embedding.embeddingStringFromJavaObject(
                                embedding
                            )
                        val similarFaces = FaceRecogniser.faceExists(
                            faces?.toList()?: emptyList(),
                            embedding.first(),
                            similarityThreshold,
                        )
                        Log.d("============> similar faces", "$similarFaces")
                        onFaceRecognised(faceString = faceEmbedding.embedding, similarFaces = similarFaces, imagePath = uri.path?:"")
                    }
                    else {
                        closeActivity(ACTIVITY_FAILED)
                    }
                } catch (ex: Exception) {
                    Log.d(
                        "FACE RECOGNITION ERROR",
                        ex.stackTraceToString()
                    )
                    closeActivity(ACTIVITY_FAILED)
                }
            }
        }.addOnFailureListener { exception ->
            Log.d(
                "FACE DETECTION",
                "face detection failed. \n ${exception.stackTraceToString()}"
            )
            closeActivity(ACTIVITY_FAILED)
        }
    }

    private fun closeActivity(activityResult: Int = Activity.RESULT_OK) {
        if (this::recMode.isInitialized) {
            intentResult.putExtra(MODE, recMode)
        }
        setResult(activityResult, intentResult)
        finish()
    }

    private fun onFaceRecognised(faceString: String, similarFaces: List<Face>, imagePath: String = "",) {
        intentResult.putExtra(FACE_STRING, faceString)
        intentResult.putExtra(IMAGE_PATH, imagePath)
        intentResult.putParcelableArrayListExtra(SIMILAR_FACES, ArrayList(similarFaces))
        closeActivity()
    }

    private fun onFaceVerificationComplete(success: Boolean) {
        intentResult.putExtra(VERIFICATION_SUCCESSFUL, success)
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