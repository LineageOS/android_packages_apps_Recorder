/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder

import android.content.DialogInterface
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.lineageos.recorder.task.DeleteRecordingTask
import org.lineageos.recorder.task.TaskExecutor
import org.lineageos.recorder.utils.PreferencesManager
import org.lineageos.recorder.utils.Utils

class DeleteLastActivity : ComponentActivity() {
    private val taskExecutor by lazy { TaskExecutor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFinishOnTouchOutside(true)

        lifecycle.addObserver(taskExecutor)

        val preferences = PreferencesManager(this)
        val uri = preferences.lastItemUri ?: run {
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_recording_message))
            .setPositiveButton(R.string.delete) { d: DialogInterface, _: Int ->
                taskExecutor.runTask(
                    DeleteRecordingTask(contentResolver, uri)
                ) {
                    d.dismiss()
                    Utils.cancelShareNotification(this)
                    preferences.setLastItemUri(null)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }
}
