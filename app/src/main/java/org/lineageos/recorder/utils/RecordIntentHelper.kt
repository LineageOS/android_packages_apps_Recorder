/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.os.bundleOf
import org.lineageos.recorder.DeleteLastActivity

object RecordIntentHelper {
    fun getShareIntent(uri: Uri?, mimeType: String?): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.setType(mimeType)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        val chooserIntent = Intent.createChooser(intent, null)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        return chooserIntent
    }

    fun getShareIntents(uris: MutableList<Uri>, mimeType: String?): Intent {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.setType(mimeType)
        intent.putExtras(
            bundleOf(
                Intent.EXTRA_STREAM to uris,
            )
        )
        val chooserIntent = Intent.createChooser(intent, null)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        return chooserIntent
    }

    fun getOpenIntent(uri: Uri?, mimeType: String?): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mimeType)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    fun getDeleteIntent(context: Context?): Intent {
        return Intent(context, DeleteLastActivity::class.java)
    }
}
