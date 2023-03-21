package com.mateendemah.face_recognition

enum class RecognitionMode{
    ENROLL,
    VERIFY,
}

enum class RecognitionState{
    SEARCHING_FACE,
    FACE_DETECTED,
    RECOGNISING,
    RECOGNISED,
    VERIFIED_SUCCESSFULLY,
    VERIFICATION_FAILED,
    SHOW_ERROR_MESSAGE,
}