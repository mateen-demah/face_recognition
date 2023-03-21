package com.mateendemah.face_recognition

import android.util.Log
import kotlin.math.sqrt

data class Embedding(
    val embedding: String,
){
    fun embeddingStringToJavaObject(): Array<FloatArray> {
        // Parse the input string into a nested list of Float values
        val nestedList = embedding.trim('[', ']')
            .split("], [")
            .map { row -> row.split(",").map { it.toFloat() } }

        // Convert the nested list to a 2D array
        return nestedList.map { it.toFloatArray() }.toTypedArray()
    }

    fun sameAs(given: String, detected: FloatArray): Boolean {
        val embeddingArray1 = Embedding(given).embeddingStringToJavaObject().first()
        var distance = 0f
        for (index in embeddingArray1.indices) {
            val difference = embeddingArray1[index] - detected[index]
            distance += difference * difference
        }
        distance = sqrt(distance.toDouble()).toFloat()
        Log.d("VERIFICATION CONFIDENCE", "$distance")
        return distance < 1.0f
    }

    companion object {
        fun embeddingStringFromJavaObject(javaArray: Array<FloatArray>): Embedding{
            return Embedding(javaArray.map { it.toTypedArray().toList() }
                .toTypedArray().toList().toString())
        }


    }
}