/*
 * Copyright (C) 2017 The LineageOS Project
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
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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

import org.lineageos.recorder.screen.OverlayService;
import org.lineageos.recorder.screen.ScreencastService;
import org.lineageos.recorder.sounds.RecorderBinder;
import org.lineageos.recorder.sounds.SoundRecorderService;
import org.lineageos.recorder.ui.SoundVisualizer;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.OnBoardingHelper;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;

public class RecorderActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_SCREEN_REC_PERMS = 439;
    private static final int REQUEST_SOUND_REC_PERMS = 440;
    private static final int REQUEST_DIALOG_ACTIVITY = 441;
    private static final int REQUEST_AUDIO_VIDEO = 442;

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

    private FloatingActionButton mScreenFab;
    private ImageView mScreenSettings;
    private ImageView mScreenLast;

    private FloatingActionButton mSoundFab;
    private ImageView mSoundLast;

    private RelativeLayout mRecordingLayout;
    private TextView mRecordingText;
    private SoundVisualizer mRecordingVisualizer;

    private final BroadcastReceiver mTelephonyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(TelephonyManager.EXTRA_STATE, -1);
                if (state == TelephonyManager.CALL_STATE_OFFHOOK &&
                        Utils.isSoundRecording(context)) {
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

        mScreenFab = findViewById(R.id.screen_fab);
        mScreenSettings = findViewById(R.id.screen_settings_icon);
        mScreenLast = findViewById(R.id.screen_last_icon);

        mSoundFab = findViewById(R.id.sound_fab);
        mSoundLast = findViewById(R.id.sound_last_icon);

        mRecordingLayout = findViewById(R.id.main_recording);
        mRecordingText = findViewById(R.id.main_recording_text);
        mRecordingVisualizer = findViewById(R.id.main_recording_visualizer);

        mScreenFab.setOnClickListener(v -> toggleScreenRecorder());
        mSoundFab.setOnClickListener(v -> toggleSoundRecorder());
        mScreenSettings.setOnClickListener(v -> openScreenSettings());
        mScreenLast.setOnClickListener(v -> openLastScreen());
        mSoundLast.setOnClickListener(v -> openLastSound());

        mPrefs = getSharedPreferences(Utils.PREFS, 0);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        bindSoundRecService();

        OnBoardingHelper.onBoardScreenSettings(this, mScreenSettings);
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
        stopOverlayService();
        refresh();
        clearTransitionNames();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        if (requestCode == REQUEST_SCREEN_REC_PERMS && hasAllScreenRecorderPermissions() ||
                requestCode == REQUEST_SOUND_REC_PERMS && hasAllAudioRecorderPermissions()) {
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
            case REQUEST_SCREEN_REC_PERMS:
                toggleScreenRecorder();
                break;
        }
    }

    private void askPermissionsAgain(int requestCode) {
        switch (requestCode) {
            case REQUEST_SOUND_REC_PERMS:
                checkSoundRecPermissions();
                break;
            case REQUEST_SCREEN_REC_PERMS:
                checkScreenRecPermissions();
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
            mSoundService.createShareNotification();
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

    private void toggleScreenRecorder() {
        if (checkScreenRecPermissions()) {
            return;
        }

        if (Utils.isScreenRecording(this)) {
            // Stop
            Utils.setStatus(this, Utils.UiStatus.NOTHING);
            startService(new Intent(ScreencastService.ACTION_STOP_SCREENCAST)
                    .setClass(this, ScreencastService.class));
        } else {
            // Start
            MediaProjectionManager mediaProjectionManager = getSystemService(
                    MediaProjectionManager.class);
            Intent permissionIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(permissionIntent, REQUEST_AUDIO_VIDEO);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_AUDIO_VIDEO && resultCode == Activity.RESULT_OK) {
            Intent intent = new Intent(this, OverlayService.class);
            intent.putExtra(OverlayService.EXTRA_HAS_AUDIO, isAudioAllowedWithScreen());
            intent.putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode);
            intent.putExtra(OverlayService.EXTRA_RESULT_DATA, data);
            startService(intent);
            finish();
        }
    }

    private void refresh() {
        ConstraintSet set = new ConstraintSet();
        if (Utils.isRecording(this)) {
            boolean screenRec = Utils.isScreenRecording(this);

            mRecordingText.setText(getString(screenRec ?
                    R.string.screen_recording_message : R.string.sound_recording_title_working));
            mRecordingLayout.setBackgroundColor(ContextCompat.getColor(this, screenRec ?
                    R.color.screen : R.color.sound));
            mRecordingVisualizer.setVisibility(screenRec ? View.GONE : View.VISIBLE);
            mScreenFab.setSelected(screenRec);
            mSoundFab.setSelected(!screenRec);

            if (screenRec) {
                mScreenFab.setImageResource(R.drawable.ic_stop_screen);
                set.clone(this, R.layout.constraint_screen);
            } else {
                mSoundFab.setImageResource(R.drawable.ic_stop_sound);
                mRecordingVisualizer.onAudioLevelUpdated(0);
                if (mSoundService != null) {
                    mSoundService.setAudioListener(mRecordingVisualizer);
                }
                set.clone(this, R.layout.constraint_sound);
            }
        } else {
            mScreenFab.setImageResource(R.drawable.ic_action_screen_record);
            mSoundFab.setImageResource(R.drawable.ic_action_sound_record);
            mScreenFab.setSelected(false);
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

    private boolean hasDrawOverOtherAppsPermission() {
        return Settings.canDrawOverlays(this);
    }

    private boolean hasAllAudioRecorderPermissions() {
        return hasAudioPermission() && hasPhoneReaderPermission();
    }

    private boolean hasAllScreenRecorderPermissions() {
        // None for now
        return true;
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

    private boolean checkScreenRecPermissions() {
        if (!hasDrawOverOtherAppsPermission()) {
            Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permissions_title)
                    .setMessage(getString(R.string.dialog_permissions_overlay))
                    .setPositiveButton(getString(R.string.screen_audio_warning_button_ask),
                            (dialog, which) -> startActivityForResult(overlayIntent, 443))
                    .show();
            return true;
        }

        return false;
    }

    private boolean isAudioAllowedWithScreen() {
        return mPrefs.getBoolean(Utils.PREF_SCREEN_WITH_AUDIO, false);
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

    private void stopOverlayService() {
        // Stop overlay service if running
        if (OverlayService.isRunning) {
            stopService(new Intent(this, OverlayService.class));
        }
    }

    private void updateLastItemStatus() {
        String lastScreen = LastRecordHelper.getLastItemPath(this, false);
        String lastSound = LastRecordHelper.getLastItemPath(this, true);

        if (lastScreen == null) {
            mScreenLast.setVisibility(View.GONE);
        } else {
            mScreenLast.setVisibility(View.VISIBLE);
            OnBoardingHelper.onBoardLastItem(this, mScreenLast, false);
        }

        if (lastSound == null) {
            mSoundLast.setVisibility(View.GONE);
        } else {
            mSoundLast.setVisibility(View.VISIBLE);
            OnBoardingHelper.onBoardLastItem(this, mSoundLast, true);
        }
    }

    private void updateSystemUIColors() {
        int statusBarColor;
        int navigationBarColor;

        if (Utils.isRecording(this)) {
            statusBarColor = ContextCompat.getColor(this, Utils.isScreenRecording(this) ?
                    R.color.screen : R.color.sound);
            navigationBarColor = statusBarColor;
        } else {
            statusBarColor = ContextCompat.getColor(this, R.color.screen);
            navigationBarColor = ContextCompat.getColor(this, R.color.sound);
        }

        getWindow().setStatusBarColor(Utils.darkenedColor(statusBarColor));
        getWindow().setNavigationBarColor(Utils.darkenedColor(navigationBarColor));
    }

    private void clearTransitionNames() {
        mScreenSettings.setTransitionName("");
        mScreenLast.setTransitionName("");
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

    private void openScreenSettings() {
        Intent intent = new Intent(this, DialogActivity.class);
        intent.putExtra(DialogActivity.EXTRA_TITLE, R.string.screen_settings_title);
        intent.putExtra(DialogActivity.EXTRA_SETTINGS_SCREEN, true);
        showDialog(intent, mScreenSettings);
    }

    private void openLastScreen() {
        Intent intent = new Intent(this, DialogActivity.class);
        intent.putExtra(DialogActivity.EXTRA_TITLE, R.string.screen_last_title);
        intent.putExtra(DialogActivity.EXTRA_LAST_SCREEN, true);
        showDialog(intent, mScreenLast);
    }

    private void openLastSound() {
        Intent intent = new Intent(this, DialogActivity.class);
        intent.putExtra(DialogActivity.EXTRA_TITLE, R.string.sound_last_title);
        intent.putExtra(DialogActivity.EXTRA_LAST_SOUND, true);
        showDialog(intent, mSoundLast);
    }
}