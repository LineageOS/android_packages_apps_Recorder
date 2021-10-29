/*
 * Copyright (C) 2017-2021 The LineageOS Project
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
package org.lineageos.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.lineageos.recorder.utils.Utils;

public class DialogActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "dialogTitle";
    private static final int REQUEST_LOCATION_PERMS = 214;

    private SwitchCompat mLocationSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        setFinishOnTouchOutside(true);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(setupAsSettingsScreen())
                .setOnDismissListener(dialogInterface -> finish())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (hasLocationPermission()) {
            toggleAfterPermissionRequest(requestCode);
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permissions_title)
                    .setMessage(getString(R.string.dialog_permissions_location))
                    .setPositiveButton(R.string.dialog_permissions_ask,
                            (dialog, position) -> {
                                dialog.dismiss();
                                askLocationPermission();
                            })
                    .setNegativeButton(R.string.dialog_permissions_dismiss,
                            (dialog, position) -> mLocationSwitch.setChecked(false))
                    .show();
        } else {
            // User has denied all the required permissions "forever"
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permissions_title)
                    .setMessage(R.string.snack_permissions_no_permission_location)
                    .setPositiveButton(R.string.dialog_permissions_dismiss, null)
                    .show();
            mLocationSwitch.setChecked(false);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @NonNull
    private View setupAsSettingsScreen() {
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_content_settings, null);
        mLocationSwitch = view.findViewById(R.id.dialog_content_settings_location_switch);
        boolean hasLocationPerm = hasLocationPermission();
        boolean tagWithLocation = Utils.getTagWithLocation(this);
        if (tagWithLocation && !hasLocationPerm) {
            Utils.setTagWithLocation(this, false);
            tagWithLocation = false;
        }
        mLocationSwitch.setChecked(tagWithLocation);
        mLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                if (hasLocationPermission()) {
                    Utils.setTagWithLocation(this, true);
                } else {
                    askLocationPermission();
                }
            } else {
                Utils.setTagWithLocation(this, false);
            }
        });

        SwitchCompat highQualitySwitch =
                view.findViewById(R.id.dialog_content_settings_high_quality_switch);
        boolean highQuality = Utils.getRecordInHighQuality(this);
        highQualitySwitch.setChecked(highQuality);
        highQualitySwitch.setOnCheckedChangeListener(((buttonView, isChecked) ->
                Utils.setRecordingHighQuality(this, isChecked)));
        if (Utils.isRecording(this)) {
            mLocationSwitch.setEnabled(false);
            highQualitySwitch.setEnabled(false);
        }

        return view;
    }

    private boolean hasLocationPermission() {
        int result = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void askLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION_PERMS);
    }

    private void toggleAfterPermissionRequest(int requestCode) {
        if (requestCode == REQUEST_LOCATION_PERMS) {
            mLocationSwitch.setChecked(true);
            Utils.setTagWithLocation(this, true);
        }
    }
}
