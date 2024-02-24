/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.task

import android.content.ContentResolver
import android.net.Uri
import java.util.function.Consumer

class DeleteAllRecordingsTask(
    private val contentResolver: ContentResolver,
    private val uris: List<Uri>,
) : Runnable {
    override fun run() {
        uris.forEach(Consumer { uri: Uri? ->
            contentResolver.delete(
                uri!!, null, null
            )
        })
    }
}
