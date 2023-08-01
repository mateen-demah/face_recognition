package com.mateendemah.face_recognition

const val MODEL_FILENAME = "mobile_face_net.tflite"
const val LABELS_FILENAME = "labelmap.txt"
const val MODEL_INPUT_SIZE = 112
const val MODEL_IS_QUANTIZED = false

// activity input extras
const val MODE = "mode"
const val FACE_STRING = "face embedding"
const val FACE_STRINGS = "face embedding list"
const val EXISTING_FACES = "existing_faces"

// activity output extras
const val SIMILAR_FACES = "similar_faces"
const val IMAGE_PATH = "image_path"

const val SUBJECT_NAME = "subject_name"
const val SUBJECT_CONTACT = "subject_contact"
const val SUBJECT_IMAGE_URI = "subject_image_uri"
const val SIMILARITY_THRESHOLD = "similarity_threshold"

const val ACTIVITY_FAILED = -404

const val ENROLL_MODE = "enroll"
const val VERIFY_MODE = "verify"

const val ERROR_400 = "bad request"
const val SUCCESS = "success"
const val VERIFICATION_SUCCESSFUL = "verification_successful"