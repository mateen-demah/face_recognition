package com.mateendemah.facerecognitionlibrary

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mateendemah.face_recognition.*
import com.mateendemah.facerecognitionlibrary.ui.theme.FaceRecognitionLibraryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.faceTestDao()
        setContent {
            FaceRecognitionLibraryTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Home(dao)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Home(faceTestDao: FaceTestDao) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val id = remember { mutableStateOf(0L) }
    val tempEmbedding = remember { mutableStateOf("") }
    val embeddings = remember { mutableStateListOf<FaceTest>() }

    val showPopUpForEnrollmentComplete = remember { mutableStateOf(false) }
    val showPopUpforStartVerification = remember { mutableStateOf(false) }
    val showPopUpForVerificationMessage = remember { mutableStateOf(false) }
    val verificationResult = remember { mutableStateOf(false) }

    val enrollIntent =
        Intent(context, RecognitionUI::class.java).apply { putExtra(MODE, ENROLL_MODE) }
    val verificationIntent =
        Intent(context, RecognitionUI::class.java).apply { putExtra(MODE, VERIFY_MODE) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("got here", "got here code is ${result.resultCode}")
                val mode = result.data?.getStringExtra(MODE)
                if (mode == ENROLL_MODE) {
                    val faceString = result.data?.getStringExtra(FACE_STRING)
                    val similarFaces = result.data?.getStringArrayListExtra(SIMILAR_FACE_STRINGS)
                    Log.d("============> got here", "returned face String = ..$faceString..")
                    Log.d("============> got here", "returned face String = ..$similarFaces..")
                    faceString?.let {
                        tempEmbedding.value = it
                        showPopUpForEnrollmentComplete.value = true
                    }
                } else if (mode == VERIFY_MODE) {
                    val success = result.data?.getBooleanExtra(VERIFICATION_SUCCESSFUL, false)
                    success?.let {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                faceTestDao.recordAVerification(
                                    result = if (success) 1 else 0,
                                    id = id.value,
                                )
                            }
                        }
                        verificationResult.value = it
                        showPopUpForVerificationMessage.value = true
                    }
                }
            }
        }

    val existingImagesMode = remember { mutableStateOf(false) }
    val similarityThreshold = remember { mutableStateOf(.75f) }
    val numOfImagesLoaded = remember { mutableStateOf(0) }
    val successfulFaceDetections = remember { mutableStateOf(0) }
    val failedFaceDetections = remember { mutableStateOf(0) }
    val successfulEnrollments = remember { mutableStateOf(0) }
    val failedEnrollments = remember { mutableStateOf(0) }
    val falsePositives = remember {mutableStateOf(0)}
    val successfulVerifications = remember {mutableStateOf(0)}
    val failedVerifications = remember {mutableStateOf(0)}

    val faceEmbeddings = remember { mutableStateListOf<String>() }

    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    val detector = FaceDetection.getClient(highAccuracyOpts)
    val recogniser = TFLiteObjectDetectionAPIModel.create(
        context.assets,
        MODEL_FILENAME,
        LABELS_FILENAME,
        MODEL_INPUT_SIZE,
        MODEL_IS_QUANTIZED
    )

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (existingImagesMode.value) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically){ Text("-", fontSize = 30.sp, modifier = Modifier.clickable { similarityThreshold.value -= 0.1f })
                        Text("${similarityThreshold.value}")
                        Text("+", fontSize = 30.sp, modifier = Modifier.clickable { similarityThreshold.value += 0.1f })
                    }
                    Text(numOfImagesLoaded.value.toString())
                    Spacer(Modifier.padding(16.dp))
                    Text("Face Detection", fontSize = 30.sp)
                    Text(
                        "successful: ${successfulFaceDetections.value}, ${
                            try {
                                "%.${2}f".format((successfulFaceDetections.value / numOfImagesLoaded.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of loaded images", color = Color.Green
                    )
                    Text(
                        "failure: ${failedFaceDetections.value}, ${
                            try {
                                "%.${2}f".format((failedFaceDetections.value / numOfImagesLoaded.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% ", color = Color.Red
                    )
                    Text("view failures")
                    Spacer(Modifier.padding(16.dp))
                    Text("Enrollment", fontSize = 30.sp)
                    Text(
                        "successful: ${successfulEnrollments.value}, ${
                            try {
                                "%.${2}f".format((successfulEnrollments.value / successfulFaceDetections.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of detectedFaces, ${
                            try {
                                "%.${2}f".format((successfulEnrollments.value / numOfImagesLoaded.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of total loaded",
                        color = Color.Green
                    )
                    Text(
                        "failure: ${failedEnrollments.value}, ${
                            try {
                                "%.${2}f".format((failedEnrollments.value / successfulFaceDetections.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of detectedFaces, ${
                            try {
                                "%.${2}f".format((failedEnrollments.value / numOfImagesLoaded.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of total loaded",
                        color = Color.Red
                    )
                    Text(
                        "false Positives: ${falsePositives.value}, ${
                            try {
                                "%.${2}f".format((falsePositives.value / successfulFaceDetections.value.toDouble()) * 100)
                            } catch (ex: ArithmeticException) {
                                0.00
                            }
                        }% of detectedFaces, ${
                            try {
                                "%.${2}f".format((falsePositives.value / numOfImagesLoaded.value.toDouble()) * 100)
                            } catch (ex: ArithmeticException) {
                                0.00
                            }
                        }% of total loaded",
                        color = Color.Red
                    )

                    Spacer(Modifier.padding(16.dp))
                    Text("Verification", fontSize = 30.sp)
                    Text(
                        "successful: ${successfulVerifications.value}, ${
                            try {
                                "%.${2}f".format((successfulVerifications.value / successfulEnrollments.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of enrolled faces, ${
                            try {
                                "%.${2}f".format((successfulVerifications.value / numOfImagesLoaded.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of total loaded",
                        color = Color.Green
                    )
                    Text(
                        "failure: ${failedVerifications.value}, ${
                            try {
                                "%.${2}f".format((failedVerifications.value / successfulEnrollments.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of enrolled faces, ${
                            try {
                                "%.${2}f".format((failedVerifications.value / numOfImagesLoaded.value.toDouble()) * 100)
                            } catch (ex: java.lang.ArithmeticException) {
                                0.00
                            }
                        }% of total loaded",
                        color = Color.Red
                    )
//                    Text(
//                        "false Positives: ${verificationFPs.value}, ${
//                            try {
//                                "%.${2}f".format((verificationFPs.value / enrolled.value.toDouble()) * 100)
//                            } catch (ex: ArithmeticException) {
//                                0.00
//                            }
//                        }% of detectedFaces, ${
//                            try {
//                                "%.${2}f".format((falsePositives.value / numOfImagesLoaded.value.toDouble()) * 100)
//                            } catch (ex: ArithmeticException) {
//                                0.00
//                            }
//                        }% of total loaded",
//                        color = Color.Red
//                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(id = R.string.enrolled_faces),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(3f)
                    )
                    Column(
                        Modifier.weight(2f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text(
                            text = stringResource(id = R.string.verifications),
                            fontWeight = FontWeight.Bold,
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(id = R.string.succeeded),
                                modifier = Modifier.weight(1f),
                                color = Color.Green,
                                textAlign = TextAlign.Center,
                            )

                            Text(
                                text = stringResource(id = R.string.failed),
                                modifier = Modifier.weight(1f),
                                color = Color.Red,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(embeddings.size) {
                        Row {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(3f)
                            ) {
                                Text(text = "${it + 1}")
                                Text(embeddings[it].identifier)
                            }

                            Text(
                                text = embeddings[it].successfulVerifications.toString(),
                                modifier = Modifier.weight(1f),
                                color = Color.Green,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = (embeddings[it].verificationAttempts - embeddings[it].successfulVerifications).toString(),
                                modifier = Modifier.weight(1f),
                                color = Color.Red,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = { existingImagesMode.value = !existingImagesMode.value },
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text("Switch Mode")
                }
            }
            if (existingImagesMode.value) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val directory =
                                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                            var imgBmp: Bitmap
                            var detectedFace: Rect? = null
                            directory?.let {
                                val images = it.listFiles()
                                images?.forEach { image ->
                                    // load image as bitmap
                                    Log.d("got here", numOfImagesLoaded.value.toString())
                                    imgBmp = BitmapFactory.decodeFile(image.absolutePath)
                                    numOfImagesLoaded.value = numOfImagesLoaded.value + 1

                                    // attempt face detection
                                    val inputImage = InputImage.fromBitmap(imgBmp, 0)
                                    detector.process(inputImage).addOnSuccessListener { faces ->
                                        if (faces.isNotEmpty()) {
                                            detectedFace = faces.first().boundingBox
                                            successfulFaceDetections.value += 1
                                        } else {
                                            failedFaceDetections.value += 1
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
                                                    Log.d("EMBEDDING", faceEmbedding.embedding)
                                                    val faceExists = faceExists(
                                                        faceEmbeddings.shuffled().subList(0, 500.coerceAtMost(faceEmbeddings.size)),
                                                        embedding.first(),
                                                        similarityThreshold.value
                                                    )

                                                    faceEmbeddings.add(faceEmbedding.embedding)
                                                    if (faceExists) {
                                                        failedEnrollments.value =
                                                            failedEnrollments.value + 1
                                                        falsePositives.value = falsePositives.value + 1
                                                    } else {
                                                        successfulEnrollments.value =
                                                            successfulEnrollments.value + 1

                                                        // attempt verification
                                                        val recResult = recogniser.recognizeImage(
                                                            scaledFaceBitmap,
                                                            true
                                                        )
                                                        val embedding2 = recognitionResult.first().extra
                                                        if (recResult.isNotEmpty()) {
                                                            val tp = isSameFace(faceEmbedding.embedding, embedding2.first(), similarityThreshold.value,)
                                                            if (tp){
                                                                successfulVerifications.value += 1
                                                            } else {
                                                                failedVerifications.value += 1
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    failedEnrollments.value =
                                                        failedEnrollments.value + 1
                                                }
                                            } catch (ex: Exception) {
                                                Log.d(
                                                    "FACE RECOGNITION ERROR",
                                                    ex.stackTraceToString()
                                                )
                                                failedEnrollments.value =
                                                    failedEnrollments.value + 1
                                            }
                                        }
                                    }.addOnFailureListener { exception ->
                                        Log.d(
                                            "FACE DETECTION",
                                            "face detection failed. \n ${exception.stackTraceToString()}"
                                        )
                                        failedFaceDetections.value += 1
                                    }.addOnCompleteListener {
                                        detectedFace = null
                                        System.gc()
                                    }

                                    delay(500)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Green.copy(alpha = .7f))
                ) {
                    Text(text = stringResource(id = R.string.load_pictures))
                }

                Button(
                    onClick = {
                        faceTestDao.clearDb()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red.copy(alpha = .7f))
                ) {
                    Text(text = stringResource(id = R.string.clear_db))
                }

            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = {
                        enrollIntent.putStringArrayListExtra(
                            FACE_STRINGS,
                            ArrayList(embeddings.map { it.embedding }),
                        )
                        launcher.launch(enrollIntent)
                    }, modifier = Modifier.weight(1f),) {
                        Text(text = stringResource(id = R.string.enroll))
                    }
                    Button(onClick = {
                        showPopUpforStartVerification.value = true
                    }, modifier = Modifier.weight(1f),) {
                        Text(text = stringResource(id = R.string.verify))
                    }
                }
            }
        }
    }

    if (showPopUpForEnrollmentComplete.value) {
        PopupWithInputAndTitle(title = "Enter Identifier", onDone = {
//            embeddings.add(Pair(it, tempEmbedding.value))
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    faceTestDao.saveAFace(
                        FaceTest(
                            timestamp = ZonedDateTime.now().toEpochSecond(),
                            embedding = tempEmbedding.value,
                            identifier = it,
                        )
                    )
                }
            }
            showPopUpForEnrollmentComplete.value = false
        })
    }

    if (showPopUpforStartVerification.value) {
        PopupWithInputAndTitle(title = "Enter number", onDone = {
            tempEmbedding.value = embeddings[it.trim().toInt() - 1].embedding
            id.value = embeddings[it.trim().toInt() - 1].id
            verificationIntent.putExtra(FACE_STRING, tempEmbedding.value)
            verificationIntent.putExtra(SUBJECT_NAME, embeddings[it.trim().toInt() - 1].identifier)
            launcher.launch(verificationIntent)
            showPopUpforStartVerification.value = false
        })
    }

    if (showPopUpForVerificationMessage.value) {
        BoldTextPopup(
            title = "",
            message = "Verification ${if (verificationResult.value) "Successful" else "Failed"}"
        ) {
            showPopUpForVerificationMessage.value = false
        }
    }

    LaunchedEffect(showPopUpForVerificationMessage.value, showPopUpForEnrollmentComplete.value) {
        coroutineScope.launch {
            val faces = withContext(Dispatchers.IO) {
                faceTestDao.getAll()
            }
            embeddings.clear()
            embeddings.addAll(faces)
        }
    }
}

@Composable
fun PopupWithInputAndTitle(
    title: String,
    onDone: (String) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val isErrorDisplayed = errorMessage.isNotEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        Card(
            Modifier
                .width(300.dp)
                .align(Alignment.Center)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        errorMessage = ""
                    },
                    label = { Text("Enter text") }
                )
                if (isErrorDisplayed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (inputText.isNotEmpty()) {
                                onDone(inputText)
                            } else {
                                errorMessage = "Please enter some text."
                            }
                        }
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
fun BoldTextPopup(title: String, message: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = title) },
        text = {
            Text(
                text = message,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        },
        buttons = {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(text = "Close")
            }
        }
    )
}

private fun faceExists(
    faceList: List<String>,
    detectedFace: FloatArray,
    threshold: Float
): Boolean {
    for (face in faceList) {
        val faceExists = isSameFace(face, detectedFace, threshold)
        if (faceExists) return true
    }
    return false
}

private fun isSameFace(given: String, detected: FloatArray, threshold: Float): Boolean {
    val embeddingArray1 = Embedding(given).embeddingStringToJavaObject().first()
    var distance = 0f
    for (index in embeddingArray1.indices) {
        val difference = embeddingArray1[index] - detected[index]
        distance += difference * difference
    }
    distance = sqrt(distance.toDouble()).toFloat()
    Log.d("VERIFICATION CONFIDENCE", "$distance")
    return distance < threshold
}