package com.mateendemah.face_recognition.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mateendemah.face_recognition.R
import com.mateendemah.face_recognition.RecognitionState

@Composable
fun MessagesBanner(
    showBanner: MutableState<Boolean>,
    recognitionState: MutableState<RecognitionState>,
    errorMessage: MutableState<String>,
) {
    AnimatedVisibility(visible = showBanner.value) {
        Text(
            when (recognitionState.value) {
                RecognitionState.SEARCHING_FACE -> stringResource(R.string.no_face_detected)
                RecognitionState.FACE_DETECTED -> stringResource(R.string.faceDetected)
                RecognitionState.RECOGNISING -> stringResource(id = R.string.running_recognition)
                RecognitionState.RECOGNISED -> stringResource(id = R.string.face_recognition_successful)
                else -> errorMessage.value
            },
            textAlign = TextAlign.Center,
            color = when (recognitionState.value) {
                RecognitionState.SHOW_ERROR_MESSAGE -> colorResource(R.color.till_red)
                RecognitionState.FACE_DETECTED -> colorResource(R.color.green_500)
                else -> Color.Black
            },
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth(),
        )
    }
}