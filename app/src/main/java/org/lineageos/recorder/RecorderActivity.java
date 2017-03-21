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
import android.app.ActivityManager;
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
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.lineageos.recorder.screen.OverlayService;
import org.lineageos.recorder.screen.ScreenFragment;
import org.lineageos.recorder.screen.ScreencastService;
import org.lineageos.recorder.sounds.RecorderBinder;
import org.lineageos.recorder.sounds.SoundFragment;
import org.lineageos.recorder.sounds.SoundRecorderService;
import org.lineageos.recorder.ui.SoundVisualizer;
import org.lineageos.recorder.ui.ViewPagerAdapter;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class RecorderActivity extends AppCompatActivity implements
        ViewPagerAdapter.PageProvider, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_STORAGE_PERMS = 439;
    private static final int REQUEST_SOUND_REC_PERMS = 440;

    private static final int PAGE_INDEX_SOUND = 0;
    private static final int PAGE_INDEX_SCREEN = 1;

    private static final int[] PAGE_TITLE_RES_IDS = {
            R.string.fragment_sounds, R.string.fragment_screen
    };

    private static final int[] PERMISSION_ERROR_MESSAGE_RES_IDS = {
            0,
            R.string.dialog_permissions_mic,
            R.string.dialog_permissions_storage,
            R.string.dialog_permissions_phone,
            R.string.dialog_permissions_mic_storage,
            R.string.dialog_permissions_mic_phone,
            R.string.dialog_permissions_storage_phone,
            R.string.dialog_permissions_mic_storage_phone
    };

    private ServiceConnection mConnection;
    private SoundRecorderService mSoundService;

    private FloatingActionButton mFab;
    private ViewPager mViewPager;
    private ViewPagerAdapter mAdapter;

    private SoundVisualizer mVisualizer;

    private SharedPreferences mPrefs;
    private int mPosition = 0;

    private final BroadcastReceiver mTelephonyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(TelephonyManager.EXTRA_STATE, -1);
                if (state == TelephonyManager.CALL_STATE_OFFHOOK && Utils.isSoundRecording(context)) {
                    toggleSoundRecorder();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_recorder);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(mView -> fabClicked());

        TabLayout tabs = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mAdapter = new ViewPagerAdapter(getSupportFragmentManager(), this);
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
                mFab.setImageResource(mPosition == PAGE_INDEX_SCREEN ?
                        R.drawable.ic_action_screen_record : R.drawable.ic_action_sound_record);
            }
        });
        tabs.setupWithViewPager(mViewPager);

        mPrefs = getSharedPreferences(Utils.PREFS, 0);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        // Bind to service
        bindSoundRecService();

        refresh();
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
        if (Utils.isScreenRecording(this)) {
            mViewPager.setCurrentItem(PAGE_INDEX_SCREEN);
        }

        stopOverlayService();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Utils.KEY_RECORDING.equals(key)) {
            refresh();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        if (hasAllPermissions()) {
            if (requestCode == ScreenFragment.REQUEST_AUDIO_PERMS) {
                getScreenFragment().refresh();
                return;
            } else if (requestCode == REQUEST_SOUND_REC_PERMS) {
                setupConnection();
            }
            fabClicked();
            return;
        }

        // Storage access permission is enough for screen recording
        if (hasStoragePermission() && mPosition == PAGE_INDEX_SCREEN) {
            fabClicked();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ||
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
            // Explain the user why the denied permission is needed
            int error = 0;

            if (!hasAudioPermission()) {
                error |= 1;
            }
            if (!hasStoragePermission()) {
                error |= 1 << 1;
            }
            if (!hasPhoneReaderPermission()) {
                error |= 1 << 2;
            }

            String message = getString(PERMISSION_ERROR_MESSAGE_RES_IDS[error]);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permissions_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.dialog_permissions_ask,
                            (dialog, position) -> fabClicked())
                    .setNegativeButton(R.string.dialog_permissions_dismiss, null)
                    .show();
        } else {
            // User has denied all the required permissions "forever"
            Snackbar.make(findViewById(R.id.coordinator), getString(
                    R.string.snack_permissions_no_permission), Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public int getCount() {
        return PAGE_TITLE_RES_IDS.length;
    }

    @Override
    public Fragment createPage(int index) {
        return index == PAGE_INDEX_SOUND ? new SoundFragment() : new ScreenFragment();
    }

    @Override
    public String getPageTitle(int index) {
        return getString(PAGE_TITLE_RES_IDS[index]);
    }

    private void setupConnection() {
        checkSoundRecPermissions();
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder) {
                mSoundService = ((RecorderBinder) binder).getService();
                SoundFragment soundFragment = getSoundFragment();

                if (mVisualizer == null && soundFragment != null) {
                    mVisualizer = soundFragment.getVisualizer();
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
            mFab.animate().scaleX(1.3f).scaleY(1.3f).setDuration(250)
                    .withEndAction(() -> mFab.animate().scaleX(0f).scaleY(0f).setDuration(750)
                            .setInterpolator(new FastOutSlowInInterpolator()).start())
                    .start();
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(this, OverlayService.class);
                intent.putExtra(OverlayService.EXTRA_HAS_AUDIO, getScreenFragment().withAudio());
                startService(intent);
                onBackPressed();
            }, 1000);
        }
    }

    private void refresh() {
        if (Utils.isRecording(this)) {
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

        SoundFragment soundFragment = getSoundFragment();
        ScreenFragment screenFragment = getScreenFragment();

        if (soundFragment != null) {
            mVisualizer = soundFragment.getVisualizer();
            if (mSoundService != null) {
                mSoundService.setAudioListener(mVisualizer);
            }
            soundFragment.refresh();
        }
        if (screenFragment != null) {
            screenFragment.refresh();
        }
    }

    private boolean hasStoragePermission() {
        int result = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
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

    private boolean hasAllPermissions() {
        return hasStoragePermission() && hasAudioPermission() && hasPhoneReaderPermission();
    }

    private boolean checkSoundRecPermissions() {
        ArrayList<String> permissions = new ArrayList<>();

        if (!hasStoragePermission()) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!hasAudioPermission()) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!hasPhoneReaderPermission()) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }

        if (permissions.isEmpty()) {
            return false;
        }

        String[] permissionArray = permissions.toArray(new String[permissions.size()]);
        requestPermissions(permissionArray, REQUEST_SOUND_REC_PERMS);
        return true;
    }

    private boolean checkScreenRecPermissions() {
        if (hasStoragePermission()) {
            return false;
        }

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

        final String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(perms, REQUEST_STORAGE_PERMS);
        return true;
    }

    private SoundFragment getSoundFragment() {
        return (SoundFragment) mAdapter.getFragment(PAGE_INDEX_SOUND);
    }

    private ScreenFragment getScreenFragment() {
        return (ScreenFragment) mAdapter.getFragment(PAGE_INDEX_SCREEN);
    }

    private void bindSoundRecService() {
        if (mSoundService == null && hasAllPermissions()) {
            setupConnection();
            bindService(new Intent(this, SoundRecorderService.class),
                    mConnection, BIND_AUTO_CREATE);
        }
    }

    private void stopOverlayService() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services =
                manager.getRunningServices(Integer.MAX_VALUE);

        // Stop overlay service if running
        services.stream().filter(info -> getPackageName().equals(info.service.getPackageName()) &&
                        OverlayService.class.getName().equals(info.service.getClassName()))
                .forEach(info -> stopService(new Intent(this, OverlayService.class)));
    }
}
