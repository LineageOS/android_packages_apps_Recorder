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
    private val contentResolver: ContentResolver?,
    private val uri: Uri,
    private val newName: String
) : Callable<Boolean> {
    override fun call(): Boolean {
        return if (contentResolver == null) {
            false
        } else {
            val cv = ContentValues()
            cv.put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
            cv.put(MediaStore.Audio.Media.TITLE, newName)
            val updated = contentResolver.update(uri, cv, null, null)
            updated == 1
        }
    }
}
