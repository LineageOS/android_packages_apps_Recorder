/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2017-2018 The LineageOS Project
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
package org.lineageos.recorder.screen;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

public class ScreencastService extends Service {

    private static final String SCREENCAST_NOTIFICATION_CHANNEL =
            "screencast_notification_channel";

    public static final String EXTRA_WITHAUDIO = "withaudio";
    public static final String ACTION_START_SCREENCAST =
            "org.lineageos.recorder.screen.ACTION_START_SCREENCAST";
    public static final String ACTION_STOP_SCREENCAST =
            "org.lineageos.recorder.screen.ACTION_STOP_SCREENCAST";
    private static final String ACTION_SCAN =
            "org.lineageos.recorder.server.display.SCAN";
    private static final String ACTION_STOP_SCAN =
            "org.lineageos.recorder.server.display.STOP_SCAN";
    static final String SCREENCASTER_NAME = "hidden:screen-recording";
    public static final int NOTIFICATION_ID = 61;
    private static final String LOGTAG = "ScreencastService";
    private long mStartTime;
    private Timer mTimer;
    private NotificationCompat.Builder mBuilder;
    private RecordingDevice mRecorder;
    private NotificationManager mNotificationManager;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_USER_BACKGROUND.equals(action) ||
                    Intent.ACTION_SHUTDOWN.equals(action)) {
                stopCasting();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        final String action = intent.getAction();
        if (action == null) {
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_SCAN:
            case ACTION_STOP_SCAN:
                return START_STICKY;
            case ACTION_START_SCREENCAST:
                return startScreencasting(intent);
            case ACTION_STOP_SCREENCAST:
                stopCasting();
                return START_STICKY;
            default:
                return START_NOT_STICKY;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mBroadcastReceiver, filter);

        mNotificationManager = getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                mNotificationManager == null || mNotificationManager.getNotificationChannel(
                        SCREENCAST_NOTIFICATION_CHANNEL) != null) {
            return;
        }

        CharSequence name = getString(R.string.screen_channel_title);
        String description = getString(R.string.screen_channel_desc);
        NotificationChannel notificationChannel =
                new NotificationChannel(SCREENCAST_NOTIFICATION_CHANNEL,
                        name, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void onDestroy() {
        stopCasting();
        unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    private int startScreencasting(Intent intent) {
        try {
            if (hasNoAvailableSpace()) {
                Toast.makeText(this, R.string.screen_insufficient_storage,
                        Toast.LENGTH_LONG).show();
                return START_NOT_STICKY;
            }

            mStartTime = SystemClock.elapsedRealtime();
            registerScreencaster(intent.getBooleanExtra(EXTRA_WITHAUDIO, false));
            mBuilder = createNotificationBuilder();
            mTimer = new Timer();
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    updateNotification();
                }
            }, 100, 1000);

            Utils.setStatus(getApplicationContext(), Utils.PREF_RECORDING_SCREEN);

            startForeground(NOTIFICATION_ID, mBuilder.build());
            return START_STICKY;
        } catch (Exception e) {
            Log.e(LOGTAG, e.getMessage());
        }

        return START_NOT_STICKY;
    }

    private boolean hasNoAvailableSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong();
        long megAvailable = bytesAvailable / 1048576;
        return megAvailable < 100;
    }

    private void updateNotification() {
        long timeElapsed = SystemClock.elapsedRealtime() - mStartTime;
        mBuilder.setContentText(getString(R.string.screen_notification_message,
                DateUtils.formatElapsedTime(timeElapsed / 1000)));
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private Point getNativeResolution() {
        DisplayManager dm = getSystemService(DisplayManager.class);
        if (dm == null) {
            return null;
        }

        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Point ret = new Point();
        try {
            display.getRealSize(ret);
        } catch (Exception e) {
            try {
                //noinspection JavaReflectionMemberAccess
                Method mGetRawH = Display.class.getMethod("getRawHeight");
                //noinspection JavaReflectionMemberAccess
                Method mGetRawW = Display.class.getMethod("getRawWidth");
                ret.x = (Integer) mGetRawW.invoke(display);
                ret.y = (Integer) mGetRawH.invoke(display);
            } catch (Exception ex) {
                display.getSize(ret);
            }
        }
        return ret;
    }

    private void cleanup() {
        String recorderPath = null;
        if (mRecorder != null) {
            recorderPath = mRecorder.getRecordingFilePath();
            mRecorder.stop();
            mRecorder = null;
        }
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        stopForeground(true);
        if (recorderPath != null) {
            sendShareNotification(recorderPath);
        }
    }


    private void registerScreencaster(boolean withAudio) {
        assert mRecorder == null;
        Point size = getNativeResolution();
        if (size == null) {
            return;
        }

        mRecorder = new RecordingDevice(this, size.x, size.y, withAudio);
        VirtualDisplay vd = mRecorder.registerVirtualDisplay(this);
        if (vd == null) {
            cleanup();
        }
    }

    private void stopCasting() {
        Utils.setStatus(getApplicationContext(), Utils.PREF_RECORDING_NOTHING);
        cleanup();

        if (hasNoAvailableSpace()) {
            Toast.makeText(this, R.string.screen_not_enough_storage, Toast.LENGTH_LONG).show();
        }
    }

    private NotificationCompat.Builder createNotificationBuilder() {
        Intent intent = new Intent(this, RecorderActivity.class);
        Intent stopRecordingIntent = new Intent(ACTION_STOP_SCREENCAST);
        stopRecordingIntent.setClass(this, ScreencastService.class);

        return new NotificationCompat.Builder(this, SCREENCAST_NOTIFICATION_CHANNEL)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_screen)
                .setContentTitle(getString(R.string.screen_notification_title))
                .setContentText(getString(R.string.screen_notification_message))
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                .addAction(R.drawable.ic_stop, getString(R.string.stop),
                        PendingIntent.getService(this, 0, stopRecordingIntent, 0));
    }

    private void sendShareNotification(String recordingFilePath) {
        mBuilder = createShareNotificationBuilder(recordingFilePath);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private NotificationCompat.Builder createShareNotificationBuilder(String file) {
        Intent intent = new Intent(this, RecorderActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);

        PendingIntent playPIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getOpenIntent(this, file, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent sharePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getShareIntent(this, file, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent deletePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getDeleteIntent(this, false),
                PendingIntent.FLAG_CANCEL_CURRENT);

        long timeElapsed = SystemClock.elapsedRealtime() - mStartTime;
        LastRecordHelper.setLastItem(this, file, timeElapsed, false);

        Log.i(LOGTAG, "Video complete: " + file);

        return new NotificationCompat.Builder(this, SCREENCAST_NOTIFICATION_CHANNEL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification_screen)
                .setContentTitle(getString(R.string.screen_notification_message_done))
                .setContentText(getString(R.string.screen_notification_message,
                        DateUtils.formatElapsedTime(timeElapsed / 1000)))
                .addAction(R.drawable.ic_play, getString(R.string.play), playPIntent)
                .addAction(R.drawable.ic_share, getString(R.string.share), sharePIntent)
                .addAction(R.drawable.ic_delete, getString(R.string.delete), deletePIntent)
                .setContentIntent(pi);
    }
}
