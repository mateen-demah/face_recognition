package com.mateendemah.face_recognition

import android.os.Parcel
import android.os.Parcelable

data class Face(
    val embedding: String,
    val identifier: String,
): Parcelable{
    constructor(parcel: Parcel) : this(
        parcel.readString().toString(),
        parcel.readString().toString(),
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(embedding)
        parcel.writeString(identifier)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Face> {
        override fun createFromParcel(parcel: Parcel): Face {
            return Face(parcel)
        }

        override fun newArray(size: Int): Array<Face?> {
            return arrayOfNulls(size)
        }
    }

}
