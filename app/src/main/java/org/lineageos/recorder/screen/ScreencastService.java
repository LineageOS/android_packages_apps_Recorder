/*
 * Copyright (C) 2013 The CyanogenMod Project
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
package org.lineageos.recorder.screen;

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
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.utils.Utils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

public class ScreencastService extends Service {
    public static final String EXTRA_WITHAUDIO = "withaudio";
    public static final String ACTION_START_SCREENCAST =
            "org.lineageos.recorder.screen.ACTION_START_SCREENCAST";
    public static final String ACTION_STOP_SCREENCAST =
            "org.lineageos.recorder.screen.ACTION_STOP_SCREENCAST";
    static final String SCREENCASTER_NAME = "hidden:screen-recording";
    private static final String LOGTAG = "ScreencastService";
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_USER_BACKGROUND) ||
                    intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
                context.startService(new Intent(ACTION_STOP_SCREENCAST)
                        .setClass(context, ScreencastService.class));
            }
        }
    };
    private long startTime;
    private Timer timer;
    private NotificationCompat.Builder mBuilder;
    private RecordingDevice mRecorder;
    private NotificationManager mNotificationManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void cleanup() {
        String recorderPath = null;
        if (mRecorder != null) {
            recorderPath = mRecorder.getRecordingFilePath();
            mRecorder.stop();
            mRecorder = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        stopForeground(true);
        if (recorderPath != null) {
            sendShareNotification(recorderPath);
        }
    }

    @Override
    public void onCreate() {
        stopCasting();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mBroadcastReceiver, filter);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        stopCasting();
        unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    private boolean hasNoAvailableSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong();
        long megAvailable = bytesAvailable / 1048576;
        return megAvailable < 100;
    }

    private void updateNotification() {
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;
        mBuilder.setContentText(getString(R.string.screen_notification_message,
                DateUtils.formatElapsedTime(timeElapsed / 1000)));
        mNotificationManager.notify(0, mBuilder.build());
    }

    private Point getNativeResolution() {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Point ret = new Point();
        try {
            display.getRealSize(ret);
        } catch (Exception e) {
            try {
                Method mGetRawH = Display.class.getMethod("getRawHeight");
                Method mGetRawW = Display.class.getMethod("getRawWidth");
                ret.x = (Integer) mGetRawW.invoke(display);
                ret.y = (Integer) mGetRawH.invoke(display);
            } catch (Exception ex) {
                display.getSize(ret);
            }
        }
        return ret;
    }        // size = new Point(1080, 1920);


    private void registerScreencaster(boolean withAudio) {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        assert mRecorder == null;
        Point size = getNativeResolution();
        // size = new Point(1080, 1920);
        mRecorder = new RecordingDevice(this, size.x, size.y, withAudio);
        VirtualDisplay vd = mRecorder.registerVirtualDisplay(this
        );
        if (vd == null)
            cleanup();
    }

    private void stopCasting() {
        Utils.setStatus(getApplicationContext(), Utils.PREF_RECORDING_NOTHING);
        cleanup();

        if (hasNoAvailableSpace()) {
            Toast.makeText(this, R.string.screen_not_enough_storage, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        if ("org.lineageos.recorder.server.display.SCAN".equals(intent.getAction())) {
            return START_STICKY;
        } else if ("org.lineageos.recorder.server.display.STOP_SCAN".equals(intent.getAction())) {
            return START_STICKY;
        } else if (TextUtils.equals(intent.getAction(), ACTION_START_SCREENCAST) ||
                TextUtils.equals(intent.getAction(), "com.cyanogenmod.ACTION_START_SCREENCAST")) {
            try {
                if (hasNoAvailableSpace()) {
                    Toast.makeText(this, R.string.screen_insufficient_storage, Toast.LENGTH_LONG).show();
                    return START_NOT_STICKY;
                }
                startTime = SystemClock.elapsedRealtime();
                boolean withAudio = intent.getBooleanExtra(EXTRA_WITHAUDIO, true);
                registerScreencaster(withAudio);
                mBuilder = createNotificationBuilder();
                mNotificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        updateNotification();
                    }
                }, 100, 1000);

                Utils.setStatus(getApplicationContext(), Utils.PREF_RECORDING_SCREEN);
                return START_STICKY;
            } catch (Exception e) {
                Log.e(LOGTAG, e.getMessage());
            }
        } else if (TextUtils.equals(intent.getAction(), ACTION_STOP_SCREENCAST)) {
            stopCasting();
        }
        return START_NOT_STICKY;
    }

    private NotificationCompat.Builder createNotificationBuilder() {
        Intent mIntent = new Intent(this, RecorderActivity.class);
        // Fake launcher intent to resume previous activity
        mIntent.setAction("android.intent.action.MAIN");
        mIntent.addCategory("android.intent.category.LAUNCHER");

        Intent stopRecording = new Intent(ACTION_STOP_SCREENCAST);
        stopRecording.setClass(this, ScreencastService.class);

        return new NotificationCompat.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_action_screen_record)
                .setContentTitle(getString(R.string.screen_notification_title))
                .setContentText(getString(R.string.screen_notification_message))
                .setContentIntent(PendingIntent.getActivity(this, 0, mIntent, 0))
                .addAction(R.drawable.ic_stop, getString(R.string.stop),
                        PendingIntent.getService(this, 0, stopRecording, 0));
    }

    private void sendShareNotification(String recordingFilePath) {
        mBuilder = createShareNotificationBuilder(recordingFilePath);
        mNotificationManager.notify(0, mBuilder.build());
    }

    private NotificationCompat.Builder createShareNotificationBuilder(String file) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("video/mp4");
        Uri uri = FileProvider.getUriForFile(getApplicationContext(),
                "org.lineageos.recorder.fileprovider", new File(file));
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, new File(file).getName());
        Intent chooserIntent = Intent.createChooser(sharingIntent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;

        Log.i(LOGTAG, "Video complete: " + uri);

        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(uri, "video/mp4");
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent =
                PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_CANCEL_CURRENT);

        return new NotificationCompat.Builder(this)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_action_screen_record)
                .setContentTitle(getString(R.string.screen_notification_message_done))
                .setContentText(getString(R.string.screen_notification_message,
                        DateUtils.formatElapsedTime(timeElapsed / 1000)))
                .addAction(R.drawable.ic_share, getString(R.string.share),
                        PendingIntent.getActivity(this, 0, chooserIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT))
                .setContentIntent(contentIntent);
    }
}
