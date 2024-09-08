/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder

import android.content.DialogInterface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.lineageos.recorder.utils.PreferencesManager
import org.lineageos.recorder.viewmodels.RecordingsViewModel

class DeleteLastActivity : ComponentActivity() {
    // View models
    private val model: RecordingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFinishOnTouchOutside(true)

        val preferences = PreferencesManager(this)
        val uri = preferences.lastItemUri ?: run {
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_recording_message))
            .setPositiveButton(R.string.delete) { d: DialogInterface, _: Int ->
                lifecycleScope.launch {
                    model.deleteRecordings(uri)
                    preferences.lastItemUri = null
                    d.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }
}
