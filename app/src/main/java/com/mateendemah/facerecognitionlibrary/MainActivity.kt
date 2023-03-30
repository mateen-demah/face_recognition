package com.mateendemah.facerecognitionlibrary

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import com.mateendemah.face_recognition.*
import com.mateendemah.facerecognitionlibrary.ui.theme.FaceRecognitionLibraryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

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

    val id = remember {mutableStateOf(0L)}
    val tempEmbedding = remember{ mutableStateOf("") }
    val embeddings = remember { mutableStateListOf<FaceTest>() }

    val showPopUpForEnrollmentComplete = remember {mutableStateOf(false)}
    val showPopUpforStartVerification = remember { mutableStateOf(false) }
    val showPopUpForVerificationMessage = remember { mutableStateOf(false) }
    val verificationResult = remember { mutableStateOf(false) }

    val enrollIntent = Intent(context, RecognitionUI::class.java).apply { putExtra(MODE, ENROLL_MODE) }
    val verificationIntent = Intent(context, RecognitionUI::class.java).apply { putExtra(MODE, VERIFY_MODE) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == Activity.RESULT_OK){
            Log.d("got here", "got here code is ${result.resultCode}")
            val mode = result.data?.getStringExtra(MODE)
            if (mode == ENROLL_MODE){
                val faceString = result.data?.getStringExtra(FACE_STRING)
                Log.d("got here", "returned face String = ..$faceString..")
                faceString?.let {
                    tempEmbedding.value = it
                    showPopUpForEnrollmentComplete.value = true
                }
            }
            else if (mode == VERIFY_MODE){
                val success = result.data?.getBooleanExtra(VERIFICATION_SUCCESSFUL, false)
                success?.let {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO){
                            faceTestDao.recordAVerification(result = if(success) 1 else 0, id = id.value,)
                        }
                    }
                    verificationResult.value = it
                    showPopUpForVerificationMessage.value = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                LazyColumn (verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    items(embeddings.size){
                        Row{
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(3f)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                enrollIntent.putStringArrayListExtra(FACE_STRINGS, ArrayList(embeddings.map{it.embedding}))
                             launcher.launch(enrollIntent)
            }, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = R.string.enroll))
            }
            Button(onClick = {
                showPopUpforStartVerification.value = true
            }, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = R.string.verify))
            }
        }
    }

    if (showPopUpForEnrollmentComplete.value){
        PopupWithInputAndTitle(title = "Enter Identifier", onDone = {
//            embeddings.add(Pair(it, tempEmbedding.value))
            coroutineScope.launch {
                withContext(Dispatchers.IO){
                    faceTestDao.saveAFace(FaceTest(timestamp = ZonedDateTime.now().toEpochSecond(), embedding = tempEmbedding.value, identifier = it,))
                }
            }
        showPopUpForEnrollmentComplete.value = false})
    }

    if (showPopUpforStartVerification.value){
        PopupWithInputAndTitle(title = "Enter number", onDone = {
            tempEmbedding.value = embeddings[it.trim().toInt()-1].embedding
            id.value = embeddings[it.trim().toInt()-1].id
            verificationIntent.putExtra(FACE_STRING, tempEmbedding.value)
            verificationIntent.putExtra(SUBJECT_NAME, embeddings[it.trim().toInt()-1].identifier)
            launcher.launch(verificationIntent)
            showPopUpforStartVerification.value = false
        })
    }

    if (showPopUpForVerificationMessage.value){
        BoldTextPopup(title = "", message = "Verification ${if(verificationResult.value) "Successful" else "Failed"}") {
            showPopUpForVerificationMessage.value = false
        }
    }

    LaunchedEffect(showPopUpForVerificationMessage.value, showPopUpForEnrollmentComplete.value){
        coroutineScope.launch {
            val faces = withContext(Dispatchers.IO){
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
