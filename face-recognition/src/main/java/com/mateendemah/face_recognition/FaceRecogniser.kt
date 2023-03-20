package com.mateendemah.face_recognition

import android.content.Context
import android.graphics.*
import android.renderscript.*
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import kotlin.math.sqrt


const val TAG = "FACE RECOGNIZER"

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
class FaceRecogniser(
    context: Context,
    private val mode: RecognitionMode,
    private val extras: MutableList<String>,
    private val faceEmbedding: String,
    private val capture: MutableState<Boolean>,
    private val boundingBoxColor: MutableState<Color>,
    private val drawFaceBoxes: (List<Rect>, imageSize: Pair<Int, Int>) -> Unit,
    private val onCapture: (images: List<DisplayImage>, result: Embedding?) -> Unit,
    private val onErrorDetected: (String?) -> Unit,
    private val onRecognitionComplete: ((success: Boolean) -> Unit)? = null,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient()
    private val recogniser = TFLiteObjectDetectionAPIModel.create(
        context.assets,
        MODEL_FILENAME,
        LABELS_FILENAME,
        MODEL_INPUT_SIZE,
        MODEL_IS_QUANTIZED
    )

    override fun analyze(image: ImageProxy) {
        val imageBitmap = getBitmap(image)
        if (capture.value){
            extras.add("mode: $mode")
            extras.add("given embedding: ${faceEmbedding.length}") }

        imageBitmap?.let {
            val inputImage = InputImage.fromBitmap(imageBitmap, 0)
            val imgSize = inputImage.width to inputImage.height

            detector.process(inputImage).addOnSuccessListener { faces ->
                drawFaceBoxes.invoke(faces.map { it.boundingBox }, imgSize)

                if (faces.size > 1) {
                    onErrorDetected.invoke("Multiple faces detected")
                }
                else if (capture.value && faces.size == 1) {
                    onErrorDetected.invoke(null)

                    val detectedFace = faces.first().boundingBox

                    try {
                        val faceBitmap = Bitmap.createBitmap(
                            imageBitmap,
                            detectedFace.left,
                            detectedFace.top,
                            detectedFace.width(),
                            detectedFace.height(),
                        )
                        val scaledFaceBitmap = Bitmap.createScaledBitmap(faceBitmap, 112, 112, true)

                        val recognitionResult = recogniser.recognizeImage(scaledFaceBitmap, true)
                        val embedding = recognitionResult.first().extra
                        val result = if (recognitionResult.isNotEmpty()) {
                                Embedding.embeddingStringFromJavaObject(embedding)
                        } else null

                        extras.add("detected embedding: ${result?.embedding?.length}")
                        result?.let {
                            if (mode == RecognitionMode.VERIFY){
                                val sameFace = sameFace(faceEmbedding, embedding.first())
                                extras.add("verification Successful: $sameFace")
                                onRecognitionComplete?.invoke(sameFace)
                                if (sameFace){
                                    boundingBoxColor.value = Color.Green
                                }
                            }
                        }
                        onCapture.invoke(
                            listOf(
                                DisplayImage(
                                    image = imageBitmap, description = "actual image"
                                ),
                                DisplayImage(
                                    image = faceBitmap,
                                    description = "selected face"
                                ),
                                DisplayImage(
                                    image = scaledFaceBitmap,
                                    description = "scaled face"
                                )
                            ),
                            result,
                        )
                    }
                    catch (ex: java.lang.IllegalArgumentException) {
                        Log.d("FACE BMP CREATION FAILED", "Face is out of preview bounds")
                        onErrorDetected.invoke("face is not in preview")
                    }
                }
                else {
                    onErrorDetected.invoke(null)
                }
            }.addOnFailureListener { exception ->
                Log.d(TAG, "face detection failed. \n ${exception.stackTraceToString()}")
            }.addOnCompleteListener {
                image.close()
            }
        }
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
}