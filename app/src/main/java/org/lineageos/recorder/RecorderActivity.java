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

import static android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.lineageos.recorder.service.RecorderBinder;
import org.lineageos.recorder.service.SoundRecorderService;
import org.lineageos.recorder.ui.WaveFormView;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.LocationHelper;
import org.lineageos.recorder.utils.OnBoardingHelper;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;

public class RecorderActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_SOUND_REC_PERMS = 440;

    private static final int[] PERMISSION_ERROR_MESSAGE_RES_IDS = {
            0,
            R.string.dialog_permissions_mic,
            R.string.dialog_permissions_phone,
            R.string.dialog_permissions_mic_phone,
    };

    private ServiceConnection mConnection;
    private SoundRecorderService mSoundService;
    private SharedPreferences mPrefs;

    private FloatingActionButton mSoundFab;
    private ImageView mPauseResume;

    private TextView mRecordingText;
    private WaveFormView mRecordingVisualizer;

    private LocationHelper mLocationHelper;

    private boolean mReturnAudio;
    private boolean mHasRecordedAudio;

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
    public void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);

        ConstraintLayout mainView = findViewById(R.id.main_root);
        mSoundFab = findViewById(R.id.sound_fab);
        mPauseResume = findViewById(R.id.sound_pause_resume);
        ImageView soundList = findViewById(R.id.sound_list_icon);
        ImageView settings = findViewById(R.id.sound_settings);

        mRecordingText = findViewById(R.id.main_title);
        mRecordingVisualizer = findViewById(R.id.main_recording_visualizer);

        mSoundFab.setOnClickListener(v -> toggleSoundRecorder());
        mPauseResume.setOnClickListener(v -> togglePause());
        soundList.setOnClickListener(v -> openList());
        settings.setOnClickListener(v -> openSettings());

        Utils.setFullScreen(getWindow(), mainView);
        Utils.setVerticalInsets(mainView);

        mPrefs = getSharedPreferences(Utils.PREFS, 0);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        mLocationHelper = new LocationHelper(this);

        if (RECORD_SOUND_ACTION.equals(getIntent().getAction())) {
            mReturnAudio = true;
            soundList.setVisibility(View.GONE);
            settings.setVisibility(View.GONE);
        }

        bindSoundRecService();

        OnBoardingHelper.onBoardList(this, soundList);
        OnBoardingHelper.onBoardSettings(this, settings);
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {

        super.onRequestPermissionsResult(requestCode, permissions, results);
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
            if (mReturnAudio && mHasRecordedAudio) {
                Utils.cancelShareNotification(this);
                promptUser();
            }
        }
    }


    private void toggleAfterPermissionRequest(int requestCode) {
        if (requestCode == REQUEST_SOUND_REC_PERMS) {
            bindSoundRecService();
            new Handler(Looper.getMainLooper()).postDelayed(this::toggleSoundRecorder, 500);
        }
    }

    private void askPermissionsAgain(int requestCode) {
        if (requestCode == REQUEST_SOUND_REC_PERMS) {
            checkSoundRecPermissions();
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

        if (Utils.isRecording(this)) {
            // Stop
            Intent stopIntent = new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_STOP);
            startService(stopIntent);
            mHasRecordedAudio = true;
        } else {
            // Start
            Intent startIntent = new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_START)
                    .putExtra(SoundRecorderService.EXTRA_LOCATION,
                            mLocationHelper.getCurrentLocationName());
            startService(startIntent);
        }
    }

    private void togglePause() {
        if (!Utils.isRecording(this)) {
            return;
        }

        if (Utils.isPaused(this)) {
            Intent resumeIntent = new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_RESUME);
            startService(resumeIntent);
        } else {
            Intent pauseIntent = new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_PAUSE);
            startService(pauseIntent);
        }
    }

    private void refresh() {
        if (Utils.isRecording(this)) {
            mSoundFab.setImageResource(R.drawable.ic_action_stop);
            mSoundFab.setSelected(true);
            mRecordingVisualizer.setVisibility(View.VISIBLE);
            mRecordingVisualizer.setAmplitude(0);
            mPauseResume.setVisibility(View.VISIBLE);
            if (Utils.isPaused(this)) {
                mRecordingText.setText(getString(R.string.sound_recording_title_paused));
                mPauseResume.setImageResource(R.drawable.ic_resume);
                mPauseResume.setContentDescription(getString(R.string.resume));
            } else {
                mRecordingText.setText(getString(R.string.sound_recording_title_working));
                mPauseResume.setImageResource(R.drawable.ic_pause);
                mPauseResume.setContentDescription(getString(R.string.pause));
            }
            if (mSoundService != null) {
                mSoundService.setAudioListener(mRecordingVisualizer);
            }
        } else {
            mRecordingText.setText(getString(R.string.main_sound_action));
            mSoundFab.setImageResource(R.drawable.ic_action_record);
            mSoundFab.setSelected(false);
            mRecordingVisualizer.setVisibility(View.INVISIBLE);
            mPauseResume.setVisibility(View.GONE);
            if (mSoundService != null) {
                mSoundService.setAudioListener(null);
            }
        }
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

    private void openSettings() {
        Intent intent = new Intent(this, DialogActivity.class);
        intent.putExtra(DialogActivity.EXTRA_TITLE, R.string.settings_title);
        startActivity(intent);
    }

    private void openList() {
        startActivity(new Intent(this, ListActivity.class));
    }

    private void confirmLastResult() {
        Intent resultIntent = new Intent().setData(LastRecordHelper.getLastItemUri(this));
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void discardLastResult() {
        LastRecordHelper.deleteRecording(this, LastRecordHelper.getLastItemUri(this), true);
        cancelResult(true);
    }

    private void cancelResult(boolean quit) {
        setResult(RESULT_CANCELED, new Intent());
        mHasRecordedAudio = false;
        if (quit) {
            finish();
        }
    }

    private void promptUser() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_result_title)
                .setMessage(R.string.confirm_result_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> confirmLastResult())
                .setNegativeButton(R.string.discard, (dialog, which) -> discardLastResult())
                .setNeutralButton(R.string.record_again, (dialog, which) -> cancelResult(false))
                .setCancelable(false)
                .show();
    }
}
