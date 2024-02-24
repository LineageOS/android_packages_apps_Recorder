/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import org.lineageos.recorder.utils.PermissionManager
import org.lineageos.recorder.utils.PreferencesManager

class DialogActivity : AppCompatActivity() {
    // Views
    private lateinit var highQualitySwitch: MaterialSwitch
    private lateinit var locationSwitch: MaterialSwitch

    private val permissionManager: PermissionManager by lazy { PermissionManager(this) }

    private val preferences by lazy { PreferencesManager(this) }

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        setFinishOnTouchOutside(true)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(R.layout.dialog_content_settings)
            .setOnDismissListener { finish() }
            .show()

        val isRecording = intent.getBooleanExtra(EXTRA_IS_RECORDING, false)
        locationSwitch = dialog.findViewById(R.id.dialog_content_settings_location_switch)!!

        setupLocationSwitch(locationSwitch, isRecording)

        highQualitySwitch = dialog.findViewById(R.id.dialog_content_settings_high_quality_switch)!!
        setupHighQualitySwitch(highQualitySwitch, isRecording)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == PermissionManager.REQUEST_CODE) {
            if (permissionManager.hasLocationPermission()) {
                toggleAfterPermissionRequest()
            } else {
                permissionManager.onLocationPermissionDenied()
                locationSwitch.isChecked = false
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out, Color.TRANSPARENT
            )
        } else {
            @Suppress("deprecation")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    private fun setupLocationSwitch(
        locationSwitch: MaterialSwitch,
        isRecording: Boolean
    ) {
        val tagWithLocation = if (preferences.tagWithLocation) {
            if (permissionManager.hasLocationPermission()) {
                true
            } else {
                // Permission revoked -> disabled feature
                preferences.tagWithLocation = false
                false
            }
        } else {
            false
        }
        locationSwitch.isChecked = tagWithLocation
        if (isRecording) {
            locationSwitch.isEnabled = false
        } else {
            locationSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    if (permissionManager.hasLocationPermission()) {
                        preferences.tagWithLocation = true
                    } else {
                        permissionManager.requestLocationPermission()
                    }
                } else {
                    preferences.tagWithLocation = false
                }
            }
        }
    }

    private fun setupHighQualitySwitch(
        highQualitySwitch: MaterialSwitch,
        isRecording: Boolean
    ) {
        val highQuality = preferences.recordInHighQuality
        highQualitySwitch.isChecked = highQuality
        if (isRecording) {
            highQualitySwitch.isEnabled = false
        } else {
            highQualitySwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                preferences.setRecordingHighQuality(isChecked)
            }
        }
    }

    private fun toggleAfterPermissionRequest() {
        locationSwitch.isChecked = true
        preferences.tagWithLocation = true
    }

    companion object {
        const val EXTRA_IS_RECORDING = "is_recording"
    }
}
