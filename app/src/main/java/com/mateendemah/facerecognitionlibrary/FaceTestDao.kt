package com.mateendemah.facerecognitionlibrary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FaceTestDao {
    @Insert
    fun saveAFace(faceTest: FaceTest)

    @Query("UPDATE $tableName SET verification_attempts = (verification_attempts + 1), successful_verifications = (successful_verifications + :result) WHERE id = :id")
    fun recordAVerification(result: Int, id: Long)

    @Query("SELECT * FROM $tableName")
    fun getAll(): List<FaceTest>

    @Query("DELETE FROM $tableName")
    fun clearDb()
}