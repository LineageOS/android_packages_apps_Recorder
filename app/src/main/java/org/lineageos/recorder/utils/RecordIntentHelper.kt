/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.lineageos.recorder.DeleteLastActivity
import org.lineageos.recorder.models.Recording

object RecordIntentHelper {
    fun buildShareIntents(vararg recordings: Recording) = Intent().apply {
        assert(recordings.isNotEmpty()) { "Uris cannot be empty" }

        if (recordings.size == 1) {
            action = Intent.ACTION_SEND
            recordings[0].let {
                putExtra(Intent.EXTRA_TITLE, it.title)
                putExtra(Intent.EXTRA_STREAM, it.uri)
                type = it.mimeType
            }
        } else {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                recordings.map { it.uri }.toCollection(ArrayList())
            )
            type = when {
                // Either audio/wav, audio/mp4a or a mix of both
                recordings.all { it.mimeType == "audio/wav" } -> "audio/wav"
                recordings.all { it.mimeType == "audio/mp4a" } -> "audio/mp4a"
                else -> {
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/wav", "audio/mp4a"))
                    "*/*"
                }
            }
        }
    }

    fun buildOpenIntent(recording: Recording) = Intent().apply {
        action = Intent.ACTION_VIEW
        setDataAndType(recording.uri, recording.mimeType)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    fun buildDeleteIntent(context: Context?) = Intent(context, DeleteLastActivity::class.java)
}
