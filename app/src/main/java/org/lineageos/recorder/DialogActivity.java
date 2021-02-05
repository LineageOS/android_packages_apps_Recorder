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

import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

public class DialogActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String EXTRA_TITLE = "dialogTitle";
    public static final String EXTRA_DELETE_LAST_RECORDING = "deleteLastItem";
    private static final int REQUEST_RECORD_AUDIO_PERMS = 213;
    private static final String TYPE_AUDIO = "audio/wav";

    private LinearLayout mRootView;
    private FrameLayout mContent;

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

        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        if (dialogTitle != 0) {
            title.setText(dialogTitle);
        }

        setupAsLastItem(true);
        animateAppearance();

        boolean deleteLastRecording = intent.getBooleanExtra(EXTRA_DELETE_LAST_RECORDING, false);
        if (deleteLastRecording) {
            deleteLastItem();
        }
    }

    @Override
    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        if (requestCode != REQUEST_RECORD_AUDIO_PERMS) {
            return;
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    }

    private void animateAppearance() {
        mRootView.setAlpha(0f);
        mRootView.animate()
                .alpha(1f)
                .setStartDelay(250)
                .start();
    }

    private void setupAsLastItem(boolean isSound) {
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
        String type = TYPE_AUDIO;
        Uri uri = LastRecordHelper.getLastItemUri(this);
        Intent intent = LastRecordHelper.getOpenIntent(uri, type);
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
        String type = TYPE_AUDIO;
        Uri uri = LastRecordHelper.getLastItemUri(this);
        startActivity(LastRecordHelper.getShareIntent(uri, type));
    }

    private View createContentView(@LayoutRes int layout) {
        LayoutInflater inflater = getLayoutInflater();
        return inflater.inflate(layout, mContent);
    }

    private boolean hasAudioPermission() {
        int result = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void askAudioPermission() {
        requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO },
                REQUEST_RECORD_AUDIO_PERMS);
    }
}