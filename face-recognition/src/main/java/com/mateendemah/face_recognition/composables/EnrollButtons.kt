package com.mateendemah.face_recognition.composables

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateendemah.face_recognition.R

@Composable
fun EnrollButtons(
    modifier: Modifier,
    scanning: MutableState<Boolean>,
    img: MutableState<Uri?>,
    saveScan: () -> Unit,
) {

    Box(
        modifier = modifier.then(
            Modifier
                .fillMaxHeight(0.15f)
                .fillMaxWidth()
                .background(color = Color.Black.copy(alpha = 0.37f))
        ),
    ) {
        if (img.value == null) {
            Button(
                onClick = {
                    if (!scanning.value) {
                        scanning.value = true
                    }
                },
                modifier = Modifier.align(alignment = Alignment.Center),
                colors = ButtonDefaults.buttonColors(backgroundColor = colorResource(R.color.green_500)),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                shape = RoundedCornerShape(size = 15.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,) {
                    if (scanning.value) {
                        Box(Modifier.size(16.dp)) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    Text(
                        stringResource(
                            id = when (scanning.value) {
                                true -> R.string.scanning
                                false -> R.string.tap_to_start_scan
                            }
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(30.dp),
                modifier = Modifier.align(alignment = Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        img.value = null
                        scanning.value = true
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorResource(R.color.green_500)),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp),
                    shape = RoundedCornerShape(size = 15.dp),
                ) {
                    Text(
                        stringResource(id = R.string.rescan),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
                Button(
                    onClick = { saveScan.invoke() },
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorResource(R.color.green_500)),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp),
                    shape = RoundedCornerShape(size = 15.dp),
                ) {
                    Text(
                        stringResource(id = R.string.save),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}