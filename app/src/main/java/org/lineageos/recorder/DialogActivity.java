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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.lineageos.recorder.utils.PermissionManager;
import org.lineageos.recorder.utils.PreferencesManager;

public class DialogActivity extends AppCompatActivity {
    public static final String EXTRA_IS_RECORDING = "is_recording";

    private PermissionManager mPermissionManager;
    private PreferencesManager mPreferences;
    private SwitchCompat mLocationSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        mPermissionManager = new PermissionManager(this);
        mPreferences = new PreferencesManager(this);

        setFinishOnTouchOutside(true);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(R.layout.dialog_content_settings)
                .setOnDismissListener(dialogInterface -> finish())
                .show();

        final boolean isRecording = getIntent().getBooleanExtra(EXTRA_IS_RECORDING, false);

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
        if (requestCode == PermissionManager.REQUEST_CODE) {
            if (mPermissionManager.hasLocationPermission()) {
                toggleAfterPermissionRequest();
            } else {
                mPermissionManager.onLocationPermissionDenied();
                mLocationSwitch.setChecked(false);
            }
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
        if (mPreferences.getTagWithLocation()) {
            if (mPermissionManager.hasLocationPermission()) {
                tagWithLocation = true;
            } else {
                // Permission revoked -> disabled feature
                mPreferences.setTagWithLocation(false);
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
                    if (mPermissionManager.hasLocationPermission()) {
                        mPreferences.setTagWithLocation(true);
                    } else {
                        mPermissionManager.requestLocationPermission();
                    }
                } else {
                    mPreferences.setTagWithLocation(false);
                }
            });
        }
    }

    private void setupHighQualitySwitch(@NonNull SwitchCompat highQualitySwitch,
                                        boolean isRecording) {
        final boolean highQuality = mPreferences.getRecordInHighQuality();
        highQualitySwitch.setChecked(highQuality);

        if (isRecording) {
            highQualitySwitch.setEnabled(false);
        } else {
            highQualitySwitch.setOnCheckedChangeListener((button, isChecked) ->
                    mPreferences.setRecordingHighQuality(isChecked));
        }
    }

    private void toggleAfterPermissionRequest() {
        mLocationSwitch.setChecked(true);
        mPreferences.setTagWithLocation(true);
    }
}
