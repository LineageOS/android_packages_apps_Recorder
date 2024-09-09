/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.models

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import org.lineageos.recorder.ext.readParcelable
import java.util.Date

data class Recording(
    val uri: Uri,
    val title: String,
    val dateAdded: Date,
    val duration: Long,
    val mimeType: String,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Uri::class)!!,
        parcel.readString()!!,
        Date(parcel.readLong()),
        parcel.readLong(),
        parcel.readString()!!,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(uri, flags)
        parcel.writeString(title)
        parcel.writeLong(dateAdded.time)
        parcel.writeLong(duration)
        parcel.writeString(mimeType)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<Recording> {
        override fun createFromParcel(parcel: Parcel) = Recording(parcel)

        override fun newArray(size: Int) = arrayOfNulls<Recording>(size)

        fun fromMediaStore(
            uri: Uri,
            title: String,
            dateAdded: Long,
            duration: Long,
            mimeType: String,
        ) = Recording(
            uri,
            title,
            Date(dateAdded * 1000),
            duration,
            mimeType,
        )
    }
}
