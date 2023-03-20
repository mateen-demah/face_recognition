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

    companion object {
        fun embeddingStringFromJavaObject(javaArray: Array<FloatArray>): Embedding{
            return Embedding(javaArray.map { it.toTypedArray().toList() }
                .toTypedArray().toList().toString())
        }
    }
}