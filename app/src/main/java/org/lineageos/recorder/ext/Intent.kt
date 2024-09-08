/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.ext

import android.content.Intent
import org.lineageos.recorder.models.Recording

fun buildShareIntent(vararg recordings: Recording) = Intent().apply {
    require(recordings.isNotEmpty()) { "No media" }

    if (recordings.size == 1) {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, recordings[0].uri)
    } else {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            recordings.map { it.uri }.toCollection(ArrayList())
        )
    }
    type = "audio/*"
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}
