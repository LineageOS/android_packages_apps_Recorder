/*
 * Copyright (C) 2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.recorder.utils;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.RECORD_AUDIO;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import org.lineageos.recorder.R;

import java.util.ArrayList;
import java.util.List;

public final class PermissionManager {
    public static final int REQUEST_CODE = 440;

    private static final int[] PERMISSION_ERROR_MESSAGE_RES_IDS = {
            0,
            R.string.dialog_permissions_mic,
            R.string.dialog_permissions_phone,
            R.string.dialog_permissions_mic_phone,
            R.string.dialog_permissions_notifications,
            R.string.dialog_permissions_mic_notifications,
            R.string.dialog_permissions_phone_notifications,
            R.string.dialog_permissions_mic_phone_notifications
    };

    private final Activity activity;
    private final NotificationManager notificationManager;

    public PermissionManager(Activity activity) {
        this.activity = activity;
        this.notificationManager = activity.getSystemService(NotificationManager.class);
    }

    public boolean requestEssentialPermissions() {
        final List<String> missingPermissions = new ArrayList<>();
        if (!hasRecordAudioPermission()) {
            missingPermissions.add(RECORD_AUDIO);
        }
        if (!hasPhoneReadStatusPermission()) {
            missingPermissions.add(READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationManager.areNotificationsEnabled()) {
            missingPermissions.add(POST_NOTIFICATIONS);
        }

        if (missingPermissions.isEmpty()) {
            return false;
        } else {
            final String[] requestArray = missingPermissions.toArray(new String[0]);
            activity.requestPermissions(requestArray, REQUEST_CODE);
            return true;
        }
    }

    public void requestLocationPermission() {
        if (!hasLocationPermission()) {
            final String[] requestArray = {ACCESS_FINE_LOCATION};
            activity.requestPermissions(requestArray, REQUEST_CODE);
        }
    }

    public boolean hasEssentialPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return hasRecordAudioPermission() && hasPhoneReadStatusPermission() &&
                    notificationManager.areNotificationsEnabled();
        } else {
            return hasRecordAudioPermission() && hasPhoneReadStatusPermission();
        }
    }

    public boolean hasRecordAudioPermission() {
        return activity.checkSelfPermission(RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasPhoneReadStatusPermission() {
        return activity.checkSelfPermission(READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasLocationPermission() {
        return activity.checkSelfPermission(ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void onEssentialPermissionsDenied() {
        if (activity.shouldShowRequestPermissionRationale(POST_NOTIFICATIONS) ||
                activity.shouldShowRequestPermissionRationale(READ_PHONE_STATE) ||
                activity.shouldShowRequestPermissionRationale(RECORD_AUDIO)) {
            // Explain the user why the denied permission is needed
            int error = 0;

            if (!hasRecordAudioPermission()) {
                error |= 1;
            }
            if (!hasPhoneReadStatusPermission()) {
                error |= 1 << 1;
            }
            if (Build.VERSION.SDK_INT >= 33 && !notificationManager.areNotificationsEnabled()) {
                error |= 1 << 2;
            }

            showPermissionRationale(PERMISSION_ERROR_MESSAGE_RES_IDS[error],
                    this::requestEssentialPermissions);
        } else {
            // User has denied all the required permissions "forever"
            showPermissionError(R.string.snack_permissions_no_permission);
        }
    }

    public void onLocationPermissionDenied() {
        if (activity.shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION)) {
            showPermissionRationale(R.string.dialog_permissions_location,
                    this::requestLocationPermission);
        } else {
            showPermissionError(R.string.snack_permissions_no_permission_location);
        }
    }

    private void showPermissionRationale(@StringRes int messageRes,
                                         Runnable requestAgain) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_permissions_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.dialog_permissions_ask,
                        (dialog, position) -> {
                            dialog.dismiss();
                            requestAgain.run();
                        })
                .setNegativeButton(R.string.dialog_permissions_dismiss, null)
                .show();
    }

    private void showPermissionError(@StringRes int messageRes) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_permissions_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.dialog_permissions_dismiss, null)
                .show();
    }
}
