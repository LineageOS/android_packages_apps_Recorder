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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import org.lineageos.recorder.screen.ScreenFragment;
import org.lineageos.recorder.screen.ScreencastService;
import org.lineageos.recorder.sounds.RecorderBinder;
import org.lineageos.recorder.sounds.SoundFragment;
import org.lineageos.recorder.sounds.SoundRecorderService;
import org.lineageos.recorder.ui.SoundVisualizer;
import org.lineageos.recorder.ui.ViewPagerAdapter;
import org.lineageos.recorder.utils.PhoneStateChangeListener;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;

public class RecorderActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMS = 439;
    private static final int REQUEST_SOUND_REC_PERMS = 440;

    private ServiceConnection mConnection;
    private SoundRecorderService mSoundService;
    private ScreenFragment mScreenFragment;
    private SoundFragment mSoundFragment;

    private FloatingActionButton mFab;
    private ViewPager mViewPager;

    private SoundVisualizer mVisualizer;

    private BroadcastReceiver mTelephonyReceiver;

    private int mPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Always recreate instance
        super.onCreate(null);

        setContentView(R.layout.activity_recorder);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(mView -> fabClicked());

        // Setup fragments
        mSoundFragment = new SoundFragment();
        mScreenFragment = new ScreenFragment();

        TabLayout mTabs = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        ViewPagerAdapter mAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        mAdapter.addFragment(mSoundFragment, getString(R.string.fragment_sounds));
        mAdapter.addFragment(mScreenFragment, getString(R.string.fragment_screen));
        mViewPager.setAdapter(mAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int mNewPosition, float mOffset,
                                       int mOffsetPixels) {
            }

            @Override
            public void onPageScrollStateChanged(int mState) {
            }

            @Override
            public void onPageSelected(int mNewPosition) {
                mPosition = mNewPosition;
                mFab.setImageResource(mPosition == 1 ?
                        R.drawable.ic_action_screen_record : R.drawable.ic_action_sound_record);
            }
        });
        mTabs.setupWithViewPager(mViewPager);

        SharedPreferences mPref = getSharedPreferences(Utils.PREFS, 0);
        mPref.registerOnSharedPreferenceChangeListener((mChanged, mKey) -> {
            if (Utils.KEY_RECORDING.equals(mKey)) {
                refresh();
            }
        });

        // Bind to service
        bindSoundRecService();

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Utils.isScreenRecording(this)) {
            mViewPager.setCurrentItem(1);
        }

        if (mTelephonyReceiver == null) {
            mTelephonyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context mContext, Intent mIntent) {
                    if (mIntent.getAction().equals("android.intent.action.PHONE_STATE")) {
                        TelephonyManager mManager =
                                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                        mManager.listen(new PhoneStateChangeListener(getApplicationContext()),
                                PhoneStateListener.LISTEN_CALL_STATE);
                    }
                }
            };
        }
    }

    @Override
    public void onDestroy() {
        if (mConnection != null) {
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int mCode, @NonNull String[] mPerms,
                                           @NonNull int[] mResults) {
        if (mCode == ScreenFragment.REQUEST_AUDIO_PERMS) {
            mScreenFragment.refresh(this);
            return;
        } else if (mCode == REQUEST_SOUND_REC_PERMS) {
            setupConnection();
        }

        if (hasAllPermissions()) {
            fabClicked();
            return;
        }

        // Storage access permission is enough for screen recording
        if (hasStoragePermission() && mPosition == 1) {
            fabClicked();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ||
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
            // Explain the user why the denied permission is needed
            int mError = 0;
            final String mMessage;

            if (!hasAudioPermission()) {
                mError |= 1;
            }

            if (!hasStoragePermission()) {
                mError |= 1 << 1;
            }

            if (!hasPhoneReaderPermission()) {
                mError |= 1 << 2;
            }

            switch (mError) {
                case 1:
                    mMessage = getString(R.string.dialog_permissions_mic);
                    break;
                case 2:
                    mMessage = getString(R.string.dialog_permissions_storage);
                    break;
                case 3:
                    mMessage = getString(R.string.dialog_permissions_phone);
                    break;
                case 4:
                    mMessage = getString(R.string.dialog_permissions_mic_storage);
                    break;
                case 5:
                    mMessage = getString(R.string.dialog_permissions_mic_phone);
                    break;
                case 6:
                    mMessage = getString(R.string.dialog_permissions_storage_phone);
                    break;
                default:
                    mMessage = getString(R.string.dialog_permissions_mic_storage_phone);
                    break;
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permissions_title)
                    .setMessage(mMessage)
                    .setPositiveButton(R.string.dialog_permissions_ask,
                            (mInterface, mPosition) -> fabClicked())
                    .setNegativeButton(R.string.dialog_permissions_dismiss, null)
                    .show();
        } else {
            // User has denied all the required permissions "forever"
            Snackbar.make(findViewById(R.id.coordinator), getString(
                    R.string.snack_permissions_no_permission), Snackbar.LENGTH_LONG).show();
        }
    }

    private void setupConnection() {
        checkSoundRecPermissions();
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                mSoundService = ((RecorderBinder) iBinder).getService();

                if (mVisualizer == null && mSoundFragment != null) {
                    mVisualizer = mSoundFragment.getVisualizer();
                }
                mSoundService.setAudioListener(mVisualizer);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                mSoundService = null;
            }
        };
    }

    private void fabClicked() {
        switch (mPosition) {
            case 0:
                toggleSoundRecorder();
                break;
            case 1:
                toggleScreenRecorder();
                break;
        }
    }

    public void toggleSoundRecorder() {
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

    public void toggleScreenRecorder() {
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
            mFab.animate().scaleX(1.3f).scaleY(1.3f).setDuration(250).start();
            new Handler().postDelayed(() -> mFab.animate().scaleX(0f).scaleY(0f).setDuration(750)
                    .setInterpolator(new FastOutSlowInInterpolator()).start(), 250);
            Intent mIntent = new Intent(ScreencastService.ACTION_START_SCREENCAST);
            mIntent.putExtra(ScreencastService.EXTRA_WITHAUDIO, mScreenFragment.withAudio());
            new Handler().postDelayed(() -> {
                startService(mIntent.setClass(this, ScreencastService.class));
                Utils.setStatus(this, Utils.UiStatus.SCREEN);
                finish();
            }, 1000);
        }
    }

    private void refresh() {
        final Context mContext = this;

        if (Utils.isRecording(mContext)) {
            if (mFab.getScaleX() != 0f) {
                mFab.animate().scaleX(0f).scaleY(0f).setDuration(750)
                        .setInterpolator(new FastOutSlowInInterpolator()).start();
            }
        } else {
            if (mFab.getScaleX() != 1f) {
                mFab.animate().scaleX(1f).scaleY(1f).setDuration(500)
                        .setInterpolator(new FastOutSlowInInterpolator())
                        .start();
            }
        }

        if (mSoundFragment != null) {
            mVisualizer = mSoundFragment.getVisualizer();
            if (mSoundService != null) {
                mSoundService.setAudioListener(mVisualizer);
            }
        }

        // Refresh fragments too
        mScreenFragment.refresh(mContext);
        mSoundFragment.refresh(mContext);
    }

    private boolean hasStoragePermission() {
        int mRes = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return mRes == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAudioPermission() {
        int mRes = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return mRes == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPhoneReaderPermission() {
        int mRes = checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
        return mRes == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAllPermissions() {
        return hasStoragePermission() && hasAudioPermission() && hasPhoneReaderPermission();
    }

    private boolean checkSoundRecPermissions() {
        ArrayList<String> mPerms = new ArrayList<>();

        if (!hasStoragePermission()) {
            mPerms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!hasAudioPermission()) {
            mPerms.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!hasPhoneReaderPermission()) {
            mPerms.add(Manifest.permission.READ_PHONE_STATE);
        }

        if (!mPerms.isEmpty()) {
            String mPermsArray[] = new String[mPerms.size()];
            mPermsArray = mPerms.toArray(mPermsArray);
            requestPermissions(mPermsArray, REQUEST_SOUND_REC_PERMS);
            return true;
        }

        return false;
    }

    private boolean checkScreenRecPermissions() {
        if (hasStoragePermission()) {
            return false;
        }

        String mPerms[] = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(mPerms, REQUEST_STORAGE_PERMS);
        return true;
    }

    private void bindSoundRecService() {
        if (mSoundService == null && hasAllPermissions()) {
            setupConnection();
            bindService(new Intent(this, SoundRecorderService.class),
                    mConnection, BIND_AUTO_CREATE);
        }
    }

}
