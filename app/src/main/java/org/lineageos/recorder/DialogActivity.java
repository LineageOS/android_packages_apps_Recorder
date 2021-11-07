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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.lineageos.recorder.utils.Utils;

public class DialogActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION_PERMS = 214;

    private SwitchCompat mLocationSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        setFinishOnTouchOutside(true);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(R.layout.dialog_content_settings)
                .setOnDismissListener(dialogInterface -> finish())
                .show();

        final boolean isRecording = Utils.isRecording(this);

        mLocationSwitch = dialog.findViewById(
                R.id.dialog_content_settings_location_switch);
        if (mLocationSwitch != null) {
            setupLocationSwitch(mLocationSwitch, isRecording);
        }
        final SwitchCompat highQualitySwitch = dialog.findViewById(
                R.id.dialog_content_settings_high_quality_switch);
        if (highQualitySwitch != null) {
            setupHighQualitySwitch(highQualitySwitch, isRecording);
        }
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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private void setupLocationSwitch(@NonNull SwitchCompat locationSwitch,
                                     boolean isRecording) {
        final boolean tagWithLocation;
        if (Utils.getTagWithLocation(this)) {
            if (hasLocationPermission()) {
                tagWithLocation = true;
            } else {
                // Permission revoked -> disabled feature
                Utils.setTagWithLocation(this, false);
                tagWithLocation = false;
            }
        } else {
            tagWithLocation = false;
        }

        locationSwitch.setChecked(tagWithLocation);

        if (isRecording) {
            locationSwitch.setEnabled(false);
        } else {
            locationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
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
        }
    }

    private void setupHighQualitySwitch(@NonNull SwitchCompat highQualitySwitch,
                                        boolean isRecording) {
        final boolean highQuality = Utils.getRecordInHighQuality(this);
        highQualitySwitch.setChecked(highQuality);

        if (isRecording) {
            highQualitySwitch.setEnabled(false);
        } else {
            highQualitySwitch.setOnCheckedChangeListener((button, isChecked) ->
                    Utils.setRecordingHighQuality(this, isChecked));
        }
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
