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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

public class DialogActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String EXTRA_TITLE = "dialogTitle";
    public static final String EXTRA_DELETE_LAST_RECORDING = "deleteLastItem";
    public static final String EXTRA_SETTINGS_SCREEN = "settingsScreen";
    private static final int REQUEST_LOCATION_PERMS = 214;
    private static final String TYPE_AUDIO = "audio/wav";

    private LinearLayout mRootView;
    private FrameLayout mContent;

    private SwitchCompat mLocationSwitch;

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.dialog_base);
        setFinishOnTouchOutside(true);

        mRootView = findViewById(R.id.dialog_root);
        TextView title = findViewById(R.id.dialog_title);
        mContent = findViewById(R.id.dialog_content);

        mPrefs = getSharedPreferences(Utils.PREFS, 0);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        Intent intent = getIntent();
        int dialogTitle = intent.getIntExtra(EXTRA_TITLE, 0);
        boolean isSettingsScreen = intent.getBooleanExtra(EXTRA_SETTINGS_SCREEN, false);

        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        if (dialogTitle != 0) {
            title.setText(dialogTitle);
        }

        if (isSettingsScreen) {
            setupAsSettingsScreen();
        } else {
            setupAsLastItem();
        }

        animateAppearance();

        boolean deleteLastRecording = intent.getBooleanExtra(EXTRA_DELETE_LAST_RECORDING, false);
        if (deleteLastRecording) {
            deleteLastItem();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
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
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Utils.PREF_TAG_WITH_LOCATION.equals(key)) {
            mLocationSwitch.setText(getTagWithLocation() ?
                    R.string.settings_location_message_on :
                    R.string.settings_location_message_off);
        }
    }

    private void animateAppearance() {
        mRootView.setAlpha(0f);
        mRootView.animate()
                .alpha(1f)
                .setStartDelay(250)
                .start();
    }

    private void setupAsLastItem() {
        View view = createContentView(R.layout.dialog_content_last_item);
        TextView description = view.findViewById(R.id.dialog_content_last_description);
        ImageView play = view.findViewById(R.id.dialog_content_last_play);
        ImageView delete = view.findViewById(R.id.dialog_content_last_delete);
        ImageView share = view.findViewById(R.id.dialog_content_last_share);

        description.setText(LastRecordHelper.getLastItemDescription(this));

        play.setOnClickListener(v -> playLastItem());
        delete.setOnClickListener(v -> deleteLastItem());
        share.setOnClickListener(v -> shareLastItem());
    }

    private void playLastItem() {
        Uri uri = LastRecordHelper.getLastItemUri(this);
        Intent intent = LastRecordHelper.getOpenIntent(uri, TYPE_AUDIO);
        if (intent != null) {
            startActivityForResult(intent, 0);
        }
    }

    private void deleteLastItem() {
        Uri uri = LastRecordHelper.getLastItemUri(this);
        AlertDialog dialog = LastRecordHelper.deleteFile(this, uri);
        dialog.setOnDismissListener(d -> finish());
        dialog.show();
    }

    private void shareLastItem() {
        Uri uri = LastRecordHelper.getLastItemUri(this);
        startActivity(LastRecordHelper.getShareIntent(uri, TYPE_AUDIO));
    }

    private void setupAsSettingsScreen() {
        final View view = createContentView(R.layout.dialog_content_settings);
        mLocationSwitch = view.findViewById(R.id.dialog_content_settings_location_switch);
        boolean hasLocationPerm = hasLocationPermission();
        setTagWithLocation(hasLocationPerm);
        mLocationSwitch.setChecked(hasLocationPerm);
        mLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (hasLocationPermission()) {
                setTagWithLocation(isChecked);
            } else if (isChecked) {
                askLocationPermission();
            } else {
                setTagWithLocation(false);
            }
        });

        mLocationSwitch.setText(getTagWithLocation() ?
                R.string.settings_location_message_on :
                R.string.settings_location_message_off);

        if (Utils.isRecording(this)) {
            mLocationSwitch.setEnabled(false);
        }
    }

    private View createContentView(@LayoutRes int layout) {
        LayoutInflater inflater = getLayoutInflater();
        return inflater.inflate(layout, mContent);
    }

    private boolean hasLocationPermission() {
        int result = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void askLocationPermission() {
        requestPermissions(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                REQUEST_LOCATION_PERMS);
    }

    private void setTagWithLocation(boolean enabled) {
        mPrefs.edit().putBoolean(Utils.PREF_TAG_WITH_LOCATION, enabled).apply();
    }

    private boolean getTagWithLocation() {
        return mPrefs.getBoolean(Utils.PREF_TAG_WITH_LOCATION, false);
    }

    private void toggleAfterPermissionRequest(int requestCode) {
        if (requestCode == REQUEST_LOCATION_PERMS) {
            mLocationSwitch.setChecked(true);
            setTagWithLocation(true);
        }
    }
}
