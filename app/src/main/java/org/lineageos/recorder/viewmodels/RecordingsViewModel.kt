/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.viewmodels

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.lineageos.recorder.ext.applicationContext
import org.lineageos.recorder.models.Recording
import org.lineageos.recorder.repository.RecordingsRepository
import org.lineageos.recorder.utils.Utils

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {
    private val contentResolver = applicationContext.contentResolver

    val recordings =
        RecordingsRepository.recordings(
            applicationContext, RecordingsRepository.recordingsUri
        )
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = listOf(),
            )

    val inSelectionMode = MutableLiveData(false)

    suspend fun deleteRecordings(vararg uris: Uri) {
        withContext(Dispatchers.IO) {
            for (uri in uris) {
                try {
                    contentResolver.delete(
                        uri,
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

    suspend fun deleteRecordings(
        vararg recordings: Recording
    ) = deleteRecordings(*recordings.map { it.uri }.toTypedArray())

    suspend fun renameRecording(recording: Recording, newName: String) {
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

    companion object {
        private val LOG_TAG = RecordingsViewModel::class.simpleName!!
    }
}
