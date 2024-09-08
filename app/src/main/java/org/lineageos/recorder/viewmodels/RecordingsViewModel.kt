/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.viewmodels

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lineageos.recorder.ext.applicationContext
import org.lineageos.recorder.models.Recording
import org.lineageos.recorder.repository.RecordingsRepository
import org.lineageos.recorder.utils.Utils
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {
    private val contentResolver = applicationContext.contentResolver

    // TODO: We use MediaStore.VOLUME_EXTERNAL on other apps
    private val recordingsUri = MediaStore.Audio.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY
    )

    val recordings = RecordingsRepository.recordings(applicationContext, recordingsUri)
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf(),
        )

    fun addRecordingToContentProvider(path: Path, mimeType: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val uri = contentResolver.insert(recordingsUri, buildCv(path, mimeType)) ?: run {
                    Log.e(LOG_TAG, "Failed to insert ${path.toAbsolutePath()}")

                    return@withContext
                }

                try {
                    contentResolver.openFileDescriptor(
                        uri, "w", null
                    )?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { oStream ->
                            Files.copy(path, oStream)
                        }
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        contentResolver.update(uri, values, null, null)
                        try {
                            Files.delete(path)
                        } catch (e: IOException) {
                            Log.w(LOG_TAG, "Failed to delete tmp file")
                        }
                        //return uri.toString()
                    }
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "Failed to write into MediaStore", e)
                }
            }
        }
    }

    fun deleteRecordings(vararg recordings: Recording) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (recording in recordings) {
                    try {
                        contentResolver.delete(
                            recording.uri,
                            null,
                            null,
                        )
                    } catch (e: SecurityException) {
                        Log.e(LOG_TAG, "Failed to delete recording", e)
                    }
                }

                Utils.cancelShareNotification(applicationContext)
            }
        }
    }

    fun renameRecording(recording: Recording, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
                    put(MediaStore.Audio.Media.TITLE, newName)
                }

                contentResolver.update(
                    recording.uri,
                    contentValues,
                    null,
                    null
                )
            }
        }
    }

    private fun buildCv(path: Path, mimeType: String) = ContentValues().apply {
        val name = path.fileName.toString()

        put(MediaStore.Audio.Media.DISPLAY_NAME, name)
        put(MediaStore.Audio.Media.TITLE, name)
        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
        put(MediaStore.Audio.Media.ARTIST, ARTIST)
        put(MediaStore.Audio.Media.ALBUM, ALBUM)
        put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
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

    companion object {
        private val LOG_TAG = RecordingsViewModel::class.simpleName!!

        private const val ARTIST = "Recorder"

        private const val ALBUM = "Sound records"

        private const val PATH = "Recordings/${ALBUM}"
        private const val PATH_LEGACY = "Music/${ALBUM}"
    }
}
