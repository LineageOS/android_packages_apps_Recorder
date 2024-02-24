/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.task

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import org.lineageos.recorder.BuildConfig
import org.lineageos.recorder.list.RecordingData
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Callable

class GetRecordingsTask(
    private val contentResolver: ContentResolver?,
) : Callable<List<RecordingData>> {
    override fun call(): List<RecordingData> {
        val list = mutableListOf<RecordingData>()

        contentResolver?.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            PROJECTION,
            "${MediaStore.Audio.Media.OWNER_PACKAGE_NAME}=?",
            arrayOf(
                BuildConfig.APPLICATION_ID,
            ),
            MY_RECORDS_SORT
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val name = cursor.getString(1)
                    val timeStamp = cursor.getLong(2)
                    val dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(timeStamp), ZoneId.systemDefault()
                    )
                    val duration = cursor.getLong(3)

                    list.add(RecordingData(uri, name, dateTime, duration))
                } while (cursor.moveToNext())
            }
        }

        return list
    }

    companion object {
        private val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION
        )
        private const val MY_RECORDS_SORT = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
    }
}
