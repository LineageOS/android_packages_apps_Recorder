/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.task

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.util.concurrent.Callable

class RenameRecordingTask(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val newName: String
) : Callable<Boolean> {
    override fun call(): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
            put(MediaStore.Audio.Media.TITLE, newName)
        }

        val updated = contentResolver.update(uri, contentValues, null, null)
        return updated == 1
    }
}
