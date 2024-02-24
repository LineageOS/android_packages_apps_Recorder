/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.task

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.Callable

class AddRecordingToContentProviderTask(
    private val cr: ContentResolver?,
    private val path: Path?,
    private val mimeType: String
) : Callable<Optional<String>> {
    override fun call(): Optional<String> {
        if (cr == null || path == null) {
            return Optional.empty()
        }
        val uri = cr.insert(
            MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            ), buildCv(path)
        )
        if (uri == null) {
            Log.e(TAG, "Failed to insert " + path.toAbsolutePath().toString())
            return Optional.empty()
        }
        try {
            cr.openFileDescriptor(uri, "w", null).use { pfd ->
                if (pfd == null) {
                    return Optional.empty()
                }
                FileOutputStream(pfd.fileDescriptor).use { oStream ->
                    Files.copy(
                        path, oStream
                    )
                }
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                cr.update(uri, values, null, null)
                try {
                    Files.delete(path)
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to delete tmp file")
                }
                return Optional.of(uri.toString())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write into MediaStore", e)
            return Optional.empty()
        }
    }

    private fun buildCv(path: Path): ContentValues {
        val name = path.fileName.toString()
        val values = ContentValues()
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, name)
        values.put(MediaStore.Audio.Media.TITLE, name)
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
        values.put(MediaStore.Audio.Media.ARTIST, ARTIST)
        values.put(MediaStore.Audio.Media.ALBUM, ALBUM)
        values.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
        values.put(
            MediaStore.Audio.Media.RELATIVE_PATH,
            if (Build.VERSION.SDK_INT >= 31) PATH else PATH_LEGACY
        )
        values.put(MediaStore.Audio.Media.IS_PENDING, 1)
        return values
    }

    companion object {
        private const val TAG = "AddRecordingToContentProviderTask"
        private const val ARTIST = "Recorder"
        private const val ALBUM = "Sound records"
        private const val PATH = "Recordings/" + ALBUM
        private const val PATH_LEGACY = "Music/" + ALBUM
    }
}
