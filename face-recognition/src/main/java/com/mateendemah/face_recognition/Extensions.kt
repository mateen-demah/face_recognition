package com.mateendemah.face_recognition

import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.CameraSelector

// STRING EXTENSIONS
fun String.toRecognitionMode(): RecognitionMode{
    return if (this == ENROLL_MODE) RecognitionMode.ENROLL else RecognitionMode.VERIFY
}

// RECT EXTENSIONS
fun Rect.transformToRectF(
    imgSize: Pair<Int, Int>,
    previewWidth: Float,
    previewHeight: Float,
    lensFacing: Int,
): RectF {
    val width = imgSize.first.toFloat()
    val height = imgSize.second.toFloat()

    val scaleX = previewWidth / width
    val scaleY = previewHeight / height

    val left =
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) (width - left).toInt() else left
    val right =
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) (width - right).toInt() else right

    val scaledLeft = scaleX * left
    val scaledTop = scaleY * top
    val scaledRight = scaleX * right
    val scaledBottom = scaleY * bottom
    return RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)
}