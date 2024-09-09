/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lineageos.recorder.flow.RecordingsFlow
import org.lineageos.recorder.models.Recording
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object RecordingsRepository {
    private val LOG_TAG = this::class.simpleName!!

    private const val ARTIST = "Recorder"

    private const val ALBUM = "Sound records"

    private const val PATH = "Recordings/${ALBUM}"
    private const val PATH_LEGACY = "Music/${ALBUM}"

    fun recordings(context: Context) = RecordingsFlow(context).flowData()

    suspend fun addRecordingToContentProvider(
        context: Context, path: Path, mimeType: String, elapsedTime: Long
    ) = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        val uri = contentResolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            buildCv(path, mimeType, elapsedTime)
        ) ?: run {
            Log.e(LOG_TAG, "Failed to insert ${path.toAbsolutePath()}")

            return@withContext null
        }

        return@withContext try {
            contentResolver.openFileDescriptor(
                uri, "w", null
            )?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { oStream ->
                    Files.copy(path, oStream)
                }
                val now = System.currentTimeMillis() / 1000L
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                    put(MediaStore.Audio.Media.SIZE, Files.size(path))
                    put(MediaStore.Audio.Media.DATE_ADDED, now)
                }
                contentResolver.update(uri, values, null, null)
                try {
                    Files.delete(path)
                } catch (e: IOException) {
                    Log.w(LOG_TAG, "Failed to delete tmp file")
                }

                Recording.fromMediaStore(
                    uri,
                    path.fileName.toString(),
                    now,
                    elapsedTime,
                    mimeType,
                )
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Failed to write into MediaStore", e)

            null
        }
    }

    private fun buildCv(path: Path, mimeType: String, elapsedTime: Long) = ContentValues().apply {
        val name = path.fileName.toString()

        put(MediaStore.Audio.Media.DISPLAY_NAME, name)
        put(MediaStore.Audio.Media.TITLE, name)
        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
        put(MediaStore.Audio.Media.ARTIST, ARTIST)
        put(MediaStore.Audio.Media.ALBUM, ALBUM)
        put(MediaStore.Audio.Media.DURATION, elapsedTime)
        put(
            MediaStore.Audio.Media.RELATIVE_PATH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PATH
            } else {
                PATH_LEGACY
            }
        )
        put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
}
