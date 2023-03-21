package com.mateendemah.facerecognitionlibrary

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateendemah.face_recognition.RecognitionMode
import com.mateendemah.face_recognition.RecognitionUi
import com.mateendemah.facerecognitionlibrary.ui.theme.FaceRecognitionLibraryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceRecognitionLibraryTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Home()
                }
            }
        }
    }
}

@Composable
fun Home() {

    val recognitionMode = remember {
        mutableStateOf(RecognitionMode.ENROLL)
    }

    val faceRecVisible = remember {
        mutableStateOf(false)
    }

    val embeddings = remember {
        mutableStateListOf<Pair<String,String>>()
    }

    val tempEmbedding = remember{ mutableStateOf("") }

    val showPopUpForEnrollmentComplete = remember {mutableStateOf(false)}
    val showPopUpforStartVerification = remember {
        mutableStateOf(false)
    }
    val showPopUpForVerificationMessage = remember {
        mutableStateOf(false)
    }
    val verificationResult = remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.enrolled_faces),
                        fontWeight = FontWeight.Bold,
                    )
                }

                LazyColumn (verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(embeddings.size){
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)){
                            Text(text = "${it + 1}")
                            Text(embeddings[it].first)
                        }
                    }
                }
            }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                recognitionMode.value = RecognitionMode.ENROLL
                faceRecVisible.value = true
            }, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = R.string.enroll))
            }
            Button(onClick = {
                recognitionMode.value = RecognitionMode.VERIFY
                showPopUpforStartVerification.value = true
            }, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = R.string.verify))
            }
        }
    }

    if (showPopUpForEnrollmentComplete.value){
        PopupWithInputAndTitle(title = "Enter Identifier", onDone = { embeddings.add(Pair(it, tempEmbedding.value))
        showPopUpForEnrollmentComplete.value = false})
    }

    if (showPopUpforStartVerification.value){
        PopupWithInputAndTitle(title = "Enter number", onDone = {
            recognitionMode.value = RecognitionMode.VERIFY
            tempEmbedding.value = embeddings[it.trim().toInt()-1].second
            faceRecVisible.value = true
            showPopUpforStartVerification.value = false
        })
    }

    if (showPopUpForVerificationMessage.value){
        BoldTextPopup(title = "", message = "Verification ${if(verificationResult.value) "Successful" else "Failed"}") {
            showPopUpForVerificationMessage.value = false
        }
    }

    RecognitionUi(
        mode = recognitionMode.value,
        visible = faceRecVisible,
        onFaceRecognised = {
            Log.d("[NOTE_r]", it)
            tempEmbedding.value = it
            faceRecVisible.value = false
            showPopUpForEnrollmentComplete.value = true
        },
        onFaceVerificationComplete = {
            verificationResult.value = it
            faceRecVisible.value = false
            showPopUpForVerificationMessage.value = true
        },
        faceEmbedding = tempEmbedding.value
    )
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
