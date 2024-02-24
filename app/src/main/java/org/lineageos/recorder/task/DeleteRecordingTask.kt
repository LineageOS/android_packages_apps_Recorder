/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.task

import android.content.ContentResolver
import android.net.Uri
import android.util.Log

class DeleteRecordingTask(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : Runnable {
    override fun run() {
        try {
            contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to delete recording", e)
        }
    }

    companion object {
        private const val TAG = "DeleteRecordingTask"
    }
}
