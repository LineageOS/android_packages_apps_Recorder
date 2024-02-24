/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.Manifest.permission
import android.app.Activity
import android.app.NotificationManager
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.lineageos.recorder.R

class PermissionManager(private val activity: Activity) {
    private val notificationManager = activity.getSystemService(NotificationManager::class.java)

    fun requestEssentialPermissions(): Boolean {
        val missingPermissions: MutableList<String> = mutableListOf()
        if (!hasRecordAudioPermission()) {
            missingPermissions.add(permission.RECORD_AUDIO)
        }
        if (!hasPhoneReadStatusPermission()) {
            missingPermissions.add(permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationManager.areNotificationsEnabled()) {
            missingPermissions.add(permission.POST_NOTIFICATIONS)
        }
        return if (missingPermissions.isEmpty()) {
            false
        } else {
            val requestArray = missingPermissions.toTypedArray<String>()
            activity.requestPermissions(requestArray, REQUEST_CODE)
            true
        }
    }

    fun requestLocationPermission() {
        if (!hasLocationPermission()) {
            val requestArray = arrayOf(permission.ACCESS_FINE_LOCATION)
            activity.requestPermissions(requestArray, REQUEST_CODE)
        }
    }

    fun hasEssentialPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasRecordAudioPermission() && hasPhoneReadStatusPermission() &&
                    notificationManager.areNotificationsEnabled()
        } else {
            hasRecordAudioPermission() && hasPhoneReadStatusPermission()
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return (activity.checkSelfPermission(permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)
    }

    fun hasPhoneReadStatusPermission(): Boolean {
        return (activity.checkSelfPermission(permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED)
    }

    fun hasLocationPermission(): Boolean {
        return (activity.checkSelfPermission(permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
    }

    fun onEssentialPermissionsDenied() {
        if (activity.shouldShowRequestPermissionRationale(permission.POST_NOTIFICATIONS) ||
            activity.shouldShowRequestPermissionRationale(permission.READ_PHONE_STATE) ||
            activity.shouldShowRequestPermissionRationale(permission.RECORD_AUDIO)
        ) {
            // Explain the user why the denied permission is needed
            var error = 0
            if (!hasRecordAudioPermission()) {
                error = error or 1
            }
            if (!hasPhoneReadStatusPermission()) {
                error = error or (1 shl 1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !notificationManager.areNotificationsEnabled()) {
                error = error or (1 shl 2)
            }
            showPermissionRationale(PERMISSION_ERROR_MESSAGE_RES_IDS[error]) {
                requestEssentialPermissions()
            }
        } else {
            // User has denied all the required permissions "forever"
            showPermissionError(R.string.snack_permissions_no_permission)
        }
    }

    fun onLocationPermissionDenied() {
        if (activity.shouldShowRequestPermissionRationale(permission.ACCESS_FINE_LOCATION)) {
            showPermissionRationale(R.string.dialog_permissions_location) {
                requestLocationPermission()
            }
        } else {
            showPermissionError(R.string.snack_permissions_no_permission_location)
        }
    }

    private fun showPermissionRationale(
        @StringRes messageRes: Int,
        requestAgain: Runnable
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_permissions_title)
            .setMessage(messageRes)
            .setPositiveButton(
                R.string.dialog_permissions_ask
            ) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                requestAgain.run()
            }
            .setNegativeButton(R.string.dialog_permissions_dismiss, null)
            .show()
    }

    private fun showPermissionError(@StringRes messageRes: Int) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_permissions_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.dialog_permissions_dismiss, null)
            .show()
    }

    companion object {
        const val REQUEST_CODE = 440
        private val PERMISSION_ERROR_MESSAGE_RES_IDS = intArrayOf(
            0,
            R.string.dialog_permissions_mic,
            R.string.dialog_permissions_phone,
            R.string.dialog_permissions_mic_phone,
            R.string.dialog_permissions_notifications,
            R.string.dialog_permissions_mic_notifications,
            R.string.dialog_permissions_phone_notifications,
            R.string.dialog_permissions_mic_phone_notifications
        )
    }
}
