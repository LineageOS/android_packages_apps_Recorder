/*
 * Copyright (C) 2017-2022 The LineageOS Project
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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.lineageos.recorder.service.ISoundRecorderFrontend;
import org.lineageos.recorder.service.RecorderBinder;
import org.lineageos.recorder.service.SoundRecorderService;
import org.lineageos.recorder.status.StatusListener;
import org.lineageos.recorder.status.StatusManager;
import org.lineageos.recorder.status.UiStatus;
import org.lineageos.recorder.task.DeleteRecordingTask;
import org.lineageos.recorder.task.TaskExecutor;
import org.lineageos.recorder.ui.WaveFormView;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.LocationHelper;
import org.lineageos.recorder.utils.OnBoardingHelper;
import org.lineageos.recorder.utils.PermissionManager;
import org.lineageos.recorder.utils.Utils;

public class RecorderActivity extends AppCompatActivity implements ISoundRecorderFrontend,
        StatusListener {
    private ServiceConnection mConnection;
    private SoundRecorderService mSoundService;

    private FloatingActionButton mSoundFab;
    private ImageView mPauseResume;

    private TextView mRecordingText;
    private TextView mElapsedTimeText;
    private WaveFormView mRecordingVisualizer;

    private LocationHelper mLocationHelper;
    private PermissionManager mPermissionManager;
    private StatusManager mStatusManager;
    private TaskExecutor mTaskExecutor;

    private boolean mReturnAudio;
    private boolean mHasRecordedAudio;

    private final StringBuilder mSbRecycle = new StringBuilder();

    private final BroadcastReceiver mTelephonyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    togglePause();
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
        mElapsedTimeText = findViewById(R.id.main_elapsed_time);
        mRecordingVisualizer = findViewById(R.id.main_recording_visualizer);

        mSoundFab.setOnClickListener(v -> toggleSoundRecorder());
        mPauseResume.setOnClickListener(v -> togglePause());
        soundList.setOnClickListener(v -> openList());
        settings.setOnClickListener(v -> openSettings());

        Utils.setFullScreen(getWindow(), mainView);
        Utils.setVerticalInsets(mainView);

        mLocationHelper = new LocationHelper(this);
        mPermissionManager = new PermissionManager(this);

        mStatusManager = StatusManager.getInstance(this);
        mStatusManager.addListener(this);

        mTaskExecutor = new TaskExecutor();
        getLifecycle().addObserver(mTaskExecutor);

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
        applyStatus(mStatusManager.getStatus());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {

        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PermissionManager.REQUEST_CODE) {
            if (mPermissionManager.hasEssentialPermissions()) {
                toggleAfterPermissionRequest();
            } else {
                mPermissionManager.onEssentialPermissionsDenied();
            }
        }
    }

    @Override
    public void setVisualizerAmplitude(int amplitude) {
        mRecordingVisualizer.post(() -> mRecordingVisualizer.setAmplitude(amplitude));
    }

    @Override
    public void setTimeElapsed(long seconds) {
        mElapsedTimeText.post(() -> mElapsedTimeText.setText(
                DateUtils.formatElapsedTime(mSbRecycle, seconds)));
    }

    @Override
    public void onStatusChanged(UiStatus newStatus) {
        applyStatus(newStatus);

        if (mReturnAudio && mHasRecordedAudio) {
            Utils.cancelShareNotification(this);
            promptUser();
        }
    }

    private void toggleAfterPermissionRequest() {
        bindSoundRecService();
        new Handler(Looper.getMainLooper()).postDelayed(this::toggleSoundRecorder, 500);
    }

    private void toggleSoundRecorder() {
        if (mPermissionManager.requestEssentialPermissions()) {
            return;
        }

        if (mSoundService == null) {
            bindSoundRecService();
            return;
        }

        if (mStatusManager.isRecording()) {
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
        if (!mStatusManager.isRecording()) {
            return;
        }

        if (mStatusManager.isPaused()) {
            Intent resumeIntent = new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_RESUME);
            startService(resumeIntent);
        } else {
            Intent pauseIntent = new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_PAUSE);
            startService(pauseIntent);
        }
    }

    private void applyStatus(UiStatus status) {
        if (UiStatus.READY.equals(status)) {
            mRecordingText.setText(getString(R.string.main_sound_action));
            mSoundFab.setImageResource(R.drawable.ic_action_record);
            mElapsedTimeText.setVisibility(View.GONE);
            mRecordingVisualizer.setVisibility(View.GONE);
            mPauseResume.setVisibility(View.GONE);
            if (mSoundService != null) {
                mSoundService.setAudioListener(null);
            }
        } else {
            mSoundFab.setImageResource(R.drawable.ic_action_stop);
            mElapsedTimeText.setVisibility(View.VISIBLE);
            mRecordingVisualizer.setVisibility(View.VISIBLE);
            mRecordingVisualizer.setAmplitude(0);
            mPauseResume.setVisibility(View.VISIBLE);
            if (UiStatus.PAUSED.equals(status)) {
                mRecordingText.setText(getString(R.string.sound_recording_title_paused));
                mPauseResume.setImageResource(R.drawable.ic_resume);
                mPauseResume.setContentDescription(getString(R.string.resume));
            } else {
                mRecordingText.setText(getString(R.string.sound_recording_title_working));
                mPauseResume.setImageResource(R.drawable.ic_pause);
                mPauseResume.setContentDescription(getString(R.string.pause));
            }
            if (mSoundService != null) {
                mSoundService.setAudioListener(this);
            }
        }
    }

    private void setupConnection() {
        mPermissionManager.requestEssentialPermissions();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                mSoundService = ((RecorderBinder) binder).getService();
                mSoundService.setAudioListener(null);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mSoundService.setAudioListener(null);
                mSoundService = null;
            }
        };
    }

    private void bindSoundRecService() {
        if (mSoundService == null && mPermissionManager.hasEssentialPermissions()) {
            setupConnection();
            bindService(new Intent(this, SoundRecorderService.class),
                    mConnection, BIND_AUTO_CREATE);
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, DialogActivity.class);
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
        final Uri uri = LastRecordHelper.getLastItemUri(this);
        if (uri != null) {
            mTaskExecutor.runTask(new DeleteRecordingTask(getContentResolver(), uri), () -> {
                Utils.cancelShareNotification(this);
                LastRecordHelper.setLastItem(this, null);
            });
        }
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
