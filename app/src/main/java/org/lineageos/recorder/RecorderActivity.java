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
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.lineageos.recorder.service.SoundRecorderService;
import org.lineageos.recorder.status.UiStatus;
import org.lineageos.recorder.task.DeleteRecordingTask;
import org.lineageos.recorder.task.TaskExecutor;
import org.lineageos.recorder.ui.WaveFormView;
import org.lineageos.recorder.utils.AppPreferences;
import org.lineageos.recorder.utils.LocationHelper;
import org.lineageos.recorder.utils.OnBoardingHelper;
import org.lineageos.recorder.utils.PermissionManager;
import org.lineageos.recorder.utils.Utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class RecorderActivity extends AppCompatActivity {
    private static final String FILE_NAME_BASE = "SoundRecords/%1$s (%2$s)";
    private static final String FILE_NAME_FALLBACK = "Sound record";

    private FloatingActionButton mSoundFab;
    private ImageView mPauseResume;

    private TextView mRecordingText;
    private TextView mElapsedTimeText;
    private WaveFormView mRecordingVisualizer;

    private LocationHelper mLocationHelper;
    private AppPreferences mPreferences;
    private PermissionManager mPermissionManager;
    private TaskExecutor mTaskExecutor;

    private boolean mReturnAudio;
    private boolean mHasRecordedAudio;

    private final StringBuilder mSbRecycle = new StringBuilder();

    @UiStatus
    private int mUiStatus = UiStatus.READY;

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

    private final Messenger mMessenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case SoundRecorderService.MSG_UI_STATUS:
                    setStatus(msg.arg1);
                    break;
                case SoundRecorderService.MSG_SOUND_AMPLITUDE:
                    setVisualizerAmplitude(msg.arg1);
                    break;
                case SoundRecorderService.MSG_TIME_ELAPSED:
                    // elapsed time is a long value split in two integer values
                    setElapsedTime(((long) msg.arg1 << 32) | msg.arg2 & 0xffffffffL);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    });
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);

            try {
                final Message msg = Message.obtain(null,
                        SoundRecorderService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };
    private Messenger mService;

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
        mPreferences = AppPreferences.getInstance(this);
        mPermissionManager = new PermissionManager(this);

        mTaskExecutor = new TaskExecutor();
        getLifecycle().addObserver(mTaskExecutor);

        if (RECORD_SOUND_ACTION.equals(getIntent().getAction())) {
            mReturnAudio = true;
            soundList.setVisibility(View.GONE);
            settings.setVisibility(View.GONE);
        }

        doBindService();

        OnBoardingHelper.onBoardList(this, soundList);
        OnBoardingHelper.onBoardSettings(this, settings);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        doUnbindService();
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

    private void setStatus(@UiStatus int status) {
        applyUiStatus(status);

        if (mReturnAudio && mHasRecordedAudio) {
            Utils.cancelShareNotification(this);
            promptUser();
        }
    }

    private void setVisualizerAmplitude(int amplitude) {
        mRecordingVisualizer.post(() -> mRecordingVisualizer.setAmplitude(amplitude));
    }

    private void setElapsedTime(long seconds) {
        mElapsedTimeText.post(() -> mElapsedTimeText.setText(
                DateUtils.formatElapsedTime(mSbRecycle, seconds)));
    }

    private void toggleAfterPermissionRequest() {
        doBindService();
        new Handler(Looper.getMainLooper()).postDelayed(this::toggleSoundRecorder, 500);
    }

    private void toggleSoundRecorder() {
        if (mPermissionManager.requestEssentialPermissions()) {
            return;
        }

        if (mConnection == null) {
            doBindService();
            return;
        }

        if (mUiStatus == UiStatus.READY) {
            // Start
            startService(new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_START)
                    .putExtra(SoundRecorderService.EXTRA_FILE_NAME, getNewRecordFileName()));
        } else {
            // Stop
            startService(new Intent(this, SoundRecorderService.class)
                    .setAction(SoundRecorderService.ACTION_STOP));
            mHasRecordedAudio = true;
        }
    }

    private void togglePause() {
        switch (mUiStatus) {
            case UiStatus.RECORDING:
                startService(new Intent(this, SoundRecorderService.class)
                        .setAction(SoundRecorderService.ACTION_PAUSE));
                break;
            case UiStatus.PAUSED:
                startService(new Intent(this, SoundRecorderService.class)
                        .setAction(SoundRecorderService.ACTION_RESUME));
                break;
            case UiStatus.READY:
                // Do nothing
                break;
        }
    }

    private void applyUiStatus(@UiStatus int status) {
        mUiStatus = status;

        if (UiStatus.READY == status) {
            mRecordingText.setText(getString(R.string.main_sound_action));
            mSoundFab.setImageResource(R.drawable.ic_action_record);
            mElapsedTimeText.setVisibility(View.GONE);
            mRecordingVisualizer.setVisibility(View.GONE);
            mPauseResume.setVisibility(View.GONE);
        } else {
            mSoundFab.setImageResource(R.drawable.ic_action_stop);
            mElapsedTimeText.setVisibility(View.VISIBLE);
            mRecordingVisualizer.setVisibility(View.VISIBLE);
            mRecordingVisualizer.setAmplitude(0);
            mPauseResume.setVisibility(View.VISIBLE);
            final Drawable prDrawable;
            if (UiStatus.PAUSED == status) {
                mRecordingText.setText(getString(R.string.sound_recording_title_paused));
                mPauseResume.setContentDescription(getString(R.string.resume));
                prDrawable = ContextCompat.getDrawable(this, R.drawable.avd_play_to_pause);
            } else {
                mRecordingText.setText(getString(R.string.sound_recording_title_working));
                mPauseResume.setContentDescription(getString(R.string.pause));
                prDrawable = ContextCompat.getDrawable(this, R.drawable.avd_pause_to_play);
            }
            mPauseResume.setTooltipText(mPauseResume.getContentDescription());
            mPauseResume.setImageDrawable(prDrawable);
            if (prDrawable instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) prDrawable).start();
            }
        }
    }

    private void doBindService() {
        if (mPermissionManager.hasEssentialPermissions()) {
            bindService(new Intent(this, SoundRecorderService.class),
                    mConnection, BIND_AUTO_CREATE);
        }
    }

    private void doUnbindService() {
        if (mService != null) {
            try {
                final Message msg = Message.obtain(null,
                        SoundRecorderService.MSG_UNREGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service
                // has crashed.
            }
        }
        unbindService(mConnection);
    }

    private void openSettings() {
        Intent intent = new Intent(this, DialogActivity.class)
                .putExtra(DialogActivity.EXTRA_IS_RECORDING, mUiStatus != UiStatus.READY);
        startActivity(intent);
    }

    private void openList() {
        startActivity(new Intent(this, ListActivity.class));
    }

    private void confirmLastResult() {
        Intent resultIntent = new Intent().setData(mPreferences.getLastItemUri());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void discardLastResult() {
        final Uri uri = mPreferences.getLastItemUri();
        if (uri != null) {
            mTaskExecutor.runTask(new DeleteRecordingTask(getContentResolver(), uri), () -> {
                Utils.cancelShareNotification(this);
                mPreferences.setLastItemUri(null);
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

    private String getNewRecordFileName() {
        final String tag = mLocationHelper.getCurrentLocationName()
                .orElse(FILE_NAME_FALLBACK);
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                getString(R.string.main_file_date_time_format),
                Locale.getDefault());
        return String.format(FILE_NAME_BASE, tag,
                formatter.format(LocalDateTime.now())) + ".%1$s";
    }
}
