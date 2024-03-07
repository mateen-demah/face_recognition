package com.mateendemah.face_recognition

import android.content.Context
import android.graphics.*
import android.renderscript.*
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.sqrt


const val TAG = "FACE RECOGNIZER"

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
class FaceRecogniser(
    context: Context,
    private val mode: RecognitionMode,
    private val faceEmbedding: String,
    private val drawFaceBoxes: (List<Rect>, imageSize: Pair<Int, Int>) -> Unit,
    private val onFaceDetected: () -> Unit,
    private val onFaceRecognised: (Array<FloatArray>) -> Unit,
    private val onErrorDetected: (String) -> Unit,
    private val onVerificationComplete: ((success: Boolean) -> Unit)? = null,
    private val shouldDoRecognition: ()->Boolean,
) : ImageAnalysis.Analyzer {

    private val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(highAccuracyOpts)
    private val recogniser = TFLiteObjectDetectionAPIModel.create(
        context.assets,
        MODEL_FILENAME,
        LABELS_FILENAME,
        MODEL_INPUT_SIZE,
        MODEL_IS_QUANTIZED
    )

    override fun analyze(image: ImageProxy) {

        val proceed = shouldDoRecognition()

        if(!proceed){
            image.close()
        } else {
            Log.d(TAG, "=============> Checkpoint // inside recogniser <==============")

            val imageBitmap = getBitmap(image)

            imageBitmap?.let {
                val inputImage = InputImage.fromBitmap(imageBitmap, 0)

                val rotation = image.imageInfo.rotationDegrees
                val reverseDim = rotation == 90 || rotation == 270
                val imgSize = if(reverseDim) Pair(image.image?.height?:0, image.image?.width?:0) else Pair(inputImage.width, inputImage.height)

                detector.process(inputImage).addOnSuccessListener { faces ->
                    drawFaceBoxes.invoke(faces.map { it.boundingBox }, imgSize)

                    if (faces.size > 1) {
                        onErrorDetected.invoke("Multiple faces detected")
                    }
                    else if (faces.size == 1) {

                        val face = faces.first()

                        onFaceDetected.invoke()
                        val detectedFace = faces.first().boundingBox

                        // face landmarks
                        val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                        val rightEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
                        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)
                        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
                        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)
                        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
                        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)

                        Log.e("checkpoint [landmark]", rightEar?.position.toString())


                        // return if landmarks aren't available
                        when (null) {
                            face.getLandmark(FaceLandmark.LEFT_EAR) -> {
                                onErrorDetected.invoke("Left Ear not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.RIGHT_EAR) -> {
                                onErrorDetected.invoke("right Ear not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.LEFT_EYE) -> {
                                onErrorDetected.invoke("Left Eye not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.RIGHT_EYE) -> {
                                onErrorDetected.invoke("Right eye not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.LEFT_CHEEK) -> {
                                onErrorDetected.invoke("Left cheek not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.RIGHT_CHEEK) -> {
                                onErrorDetected.invoke("Left Ear not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.MOUTH_BOTTOM) -> {
                                onErrorDetected.invoke("Bottom part of mouth not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.MOUTH_RIGHT) -> {
                                onErrorDetected.invoke("Right part of mouth not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.MOUTH_LEFT) -> {
                                onErrorDetected.invoke("Left part of mouth not visible")
                                return@addOnSuccessListener
                            }
                            face.getLandmark(FaceLandmark.NOSE_BASE) -> {
                                onErrorDetected.invoke("Nose is not visible")
                                return@addOnSuccessListener
                            }
                        }

                        // return if eyes are closed
                        if (face.leftEyeOpenProbability == null || face.leftEyeOpenProbability!! < 0.35) {
                            onErrorDetected.invoke("Right eye closed")
                            return@addOnSuccessListener
                        } else if (face.rightEyeOpenProbability == null || face.rightEyeOpenProbability!! < 0.35) {
                            onErrorDetected.invoke("Left eye closed")
                            return@addOnSuccessListener
                        }

                        try {
                            val faceBitmap = Bitmap.createBitmap(
                                imageBitmap,
                                detectedFace.left,
                                detectedFace.top,
                                detectedFace.width(),
                                detectedFace.height(),
                            )
                            val scaledFaceBitmap = Bitmap.createScaledBitmap(
                                faceBitmap,
                                MODEL_INPUT_SIZE,
                                MODEL_INPUT_SIZE,
                                true
                            )

                            val recognitionResult = recogniser.recognizeImage(scaledFaceBitmap, true)
                            if(recognitionResult.isNotEmpty()) {
                                val embedding = recognitionResult.first().extra

                                if (mode == RecognitionMode.VERIFY) {
                                    val sameFace = isSameFace(faceEmbedding, embedding.first())
                                    onVerificationComplete?.invoke(sameFace)
                                }
                                else {
                                    onFaceRecognised.invoke(embedding)
                                }
                            }
                        } catch (ex: java.lang.IllegalArgumentException) {
                            Log.d(
                                "FACE BMP CREATION FAILED",
                                "Face is out of preview bounds: ${ex.stackTraceToString()}"
                            )
                            onErrorDetected.invoke("face is not in preview")
                        }
                    }
                }.addOnFailureListener { exception ->
                    Log.d(TAG, "face detection failed. \n ${exception.stackTraceToString()}")
                }.addOnCompleteListener {
                    image.close()
                }
            }

        }
    }


    companion object {

        fun faceExists(
            faceList: List<Face>,
            detectedFace: Array<FloatArray>,
            threshold: Float = 0.65f,
        ): List<Face> {
            val similarFaces = mutableListOf<Face>()
            for (face in faceList) {
                val faceExists = isSameFace(face.embedding, detectedFace.first(), threshold)
                if (faceExists) similarFaces.add(face)
            }
            return similarFaces
        }

        private fun isSameFace(given: String, detected: FloatArray, threshold: Float): Boolean {
            val embeddingArray1 = Embedding(given).embeddingStringToJavaObject().first()
            var distance = 0f
            for (index in embeddingArray1.indices) {
                val difference = embeddingArray1[index] - detected[index]
                distance += difference * difference
            }
            distance = sqrt(distance.toDouble()).toFloat()
            Log.d("============> CONFIDENCE", "$distance")
            return distance < threshold
        }

        fun isSameFace(given: String, detected: FloatArray): Boolean {
            val embeddingArray1 = Embedding(given).embeddingStringToJavaObject().first()
            var distance = 0f
            for (index in embeddingArray1.indices) {
                val difference = embeddingArray1[index] - detected[index]
                distance += difference * difference
            }
            distance = sqrt(distance.toDouble()).toFloat()
            Log.d("VERIFICATION CONFIDENCE", "$distance")
            return distance < .8f
        }
        }
    }
