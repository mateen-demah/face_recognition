package com.mateendemah.facerecognitionlibrary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val tableName = "face_tests"
@Entity(tableName = tableName)
data class FaceTest (
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id")  val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "embedding") val embedding: String,
    @ColumnInfo(name = "identifier") val identifier: String,
    @ColumnInfo(name = "verification_attempts") val verificationAttempts: Int = 0,
    @ColumnInfo(name = "successful_verifications") val successfulVerifications: Int = 0,
)