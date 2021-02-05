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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.transition.TransitionManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.lineageos.recorder.sounds.RecorderBinder;
import org.lineageos.recorder.sounds.SoundRecorderService;
import org.lineageos.recorder.ui.SoundVisualizer;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.OnBoardingHelper;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;

public class RecorderActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_SOUND_REC_PERMS = 440;
    private static final int REQUEST_DIALOG_ACTIVITY = 441;

    private static final int[] PERMISSION_ERROR_MESSAGE_RES_IDS = {
            0,
            R.string.dialog_permissions_mic,
            R.string.dialog_permissions_phone,
            R.string.dialog_permissions_mic_phone,
    };

    private ServiceConnection mConnection;
    private SoundRecorderService mSoundService;
    private SharedPreferences mPrefs;

    private ConstraintLayout mConstraintRoot;

    private FloatingActionButton mSoundFab;
    private ImageView mSoundLast;

    private TextView mRecordingText;
    private SoundVisualizer mRecordingVisualizer;

    private final BroadcastReceiver mTelephonyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(TelephonyManager.EXTRA_STATE, -1);
                if (state == TelephonyManager.CALL_STATE_OFFHOOK &&
                        Utils.isRecording(context)) {
                    toggleSoundRecorder();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activty_constraint);

        mConstraintRoot = findViewById(R.id.main_root);

        mSoundFab = findViewById(R.id.sound_fab);
        mSoundLast = findViewById(R.id.sound_last_icon);

        mRecordingText = findViewById(R.id.main_recording_text);
        mRecordingVisualizer = findViewById(R.id.main_recording_visualizer);

        mSoundFab.setOnClickListener(v -> toggleSoundRecorder());
        mSoundLast.setOnClickListener(v -> openLastSound());

        mPrefs = getSharedPreferences(Utils.PREFS, 0);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        bindSoundRecService();
    }

    @Override
    public void onDestroy() {
        if (mConnection != null) {
            unbindService(mConnection);
        }
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mTelephonyReceiver,
                new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mTelephonyReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        clearTransitionNames();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {

        if (hasAllAudioRecorderPermissions()) {
            toggleAfterPermissionRequest(requestCode);
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ||
                shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
            // Explain the user why the denied permission is needed
            int error = 0;

            if (!hasAudioPermission()) {
                error |= 1;
            }
            if (!hasPhoneReaderPermission()) {
                error |= 1 << 1;
            }

            String message = getString(PERMISSION_ERROR_MESSAGE_RES_IDS[error]);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permissions_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.dialog_permissions_ask,
                            (dialog, position) -> {
                                dialog.dismiss();
                                askPermissionsAgain(requestCode);
                            })
                    .setNegativeButton(R.string.dialog_permissions_dismiss, null)
                    .show();
        } else {
            // User has denied all the required permissions "forever"
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permissions_title)
                    .setMessage(R.string.snack_permissions_no_permission)
                    .setPositiveButton(R.string.dialog_permissions_dismiss, null)
                    .show();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Utils.KEY_RECORDING.equals(key)) {
            refresh();
        }
    }


    private void toggleAfterPermissionRequest(int requestCode) {
        switch (requestCode) {
            case REQUEST_SOUND_REC_PERMS:
                bindSoundRecService();
                new Handler().postDelayed(this::toggleSoundRecorder, 500);
                break;
        }
    }

    private void askPermissionsAgain(int requestCode) {
        switch (requestCode) {
            case REQUEST_SOUND_REC_PERMS:
                checkSoundRecPermissions();
                break;
        }
    }

    private void toggleSoundRecorder() {
        if (checkSoundRecPermissions()) {
            return;
        }

        if (mSoundService == null) {
            bindSoundRecService();
            return;
        }

        if (mSoundService.isRecording()) {
            // Stop
            mSoundService.stopRecording();
            stopService(new Intent(this, SoundRecorderService.class));
            Utils.setStatus(this, Utils.UiStatus.NOTHING);
        } else {
            // Start
            startService(new Intent(this, SoundRecorderService.class));
            mSoundService.startRecording();
            Utils.setStatus(this, Utils.UiStatus.SOUND);
        }
        refresh();
    }

    private void refresh() {
        ConstraintSet set = new ConstraintSet();
        if (Utils.isRecording(this)) {
            mRecordingText.setText(getString(R.string.sound_recording_title_working));
            mSoundFab.setImageResource(R.drawable.ic_stop_sound);
            mSoundFab.setSelected(true);
            mRecordingVisualizer.setVisibility(View.VISIBLE);
            mRecordingVisualizer.onAudioLevelUpdated(0);
            if (mSoundService != null) {
                mSoundService.setAudioListener(mRecordingVisualizer);
            }
            set.clone(this, R.layout.constraint_sound);
        } else {
            mRecordingText.setText(getString(R.string.main_sound_action));
            mSoundFab.setImageResource(R.drawable.ic_action_sound_record);
            mSoundFab.setSelected(false);
            mRecordingVisualizer.setVisibility(View.GONE);
            set.clone(this, R.layout.constraint_default);
        }

        updateLastItemStatus();
        updateSystemUIColors();

        TransitionManager.beginDelayedTransition(mConstraintRoot);
        set.applyTo(mConstraintRoot);
    }

    private boolean hasAudioPermission() {
        int result = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPhoneReaderPermission() {
        int result = checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAllAudioRecorderPermissions() {
        return hasAudioPermission() && hasPhoneReaderPermission();
    }

    private boolean checkSoundRecPermissions() {
        ArrayList<String> permissions = new ArrayList<>();

        if (!hasAudioPermission()) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!hasPhoneReaderPermission()) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }

        if (permissions.isEmpty()) {
            return false;
        }

        String[] permissionArray = permissions.toArray(new String[0]);
        requestPermissions(permissionArray, REQUEST_SOUND_REC_PERMS);
        return true;
    }

    private void setupConnection() {
        checkSoundRecPermissions();
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                mSoundService = ((RecorderBinder) binder).getService();
                mSoundService.setAudioListener(mRecordingVisualizer);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mSoundService = null;
            }
        };
    }

    private void bindSoundRecService() {
        if (mSoundService == null && hasAllAudioRecorderPermissions()) {
            setupConnection();
            bindService(new Intent(this, SoundRecorderService.class),
                    mConnection, BIND_AUTO_CREATE);
        }
    }

    private void updateLastItemStatus() {
        Uri lastSound = LastRecordHelper.getLastItemUri(this);

        if (lastSound == null) {
            mSoundLast.setVisibility(View.GONE);
        } else {
            mSoundLast.setVisibility(View.VISIBLE);
            OnBoardingHelper.onBoardLastItem(this, mSoundLast);
        }
    }

    private void updateSystemUIColors() {
        int statusBarColor;
        int navigationBarColor;

        statusBarColor = ContextCompat.getColor(this, R.color.sound);
        navigationBarColor = statusBarColor;

        getWindow().setStatusBarColor(Utils.darkenedColor(statusBarColor));
        getWindow().setNavigationBarColor(Utils.darkenedColor(navigationBarColor));
    }

    private void clearTransitionNames() {
        mSoundLast.setTransitionName("");
    }

    private void showDialog(Intent intent, View view) {
        String transitionName = getString(R.string.transition_dialog_name);
        view.setTransitionName(transitionName);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(this,
                view, transitionName);
        ActivityCompat.startActivityForResult(this, intent,
                REQUEST_DIALOG_ACTIVITY, options.toBundle());
    }

    private void openLastSound() {
        Intent intent = new Intent(this, DialogActivity.class);
        intent.putExtra(DialogActivity.EXTRA_TITLE, R.string.sound_last_title);
        showDialog(intent, mSoundLast);
    }
}
