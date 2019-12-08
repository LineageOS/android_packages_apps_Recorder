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

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.MediaProviderHelper;
import org.lineageos.recorder.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ScreencastService extends Service implements MediaProviderHelper.OnContentWritten {
    private static final String LOGTAG = "ScreencastService";

    private static final String SCREENCAST_NOTIFICATION_CHANNEL =
            "screencast_notification_channel";

    private static final String EXTRA_RESULT_CODE = "extra_resultCode";
    private static final String EXTRA_DATA = "extra_data";
    private static final String EXTRA_USE_AUDIO = "extra_useAudio";

    private static final String ACTION_START_SCREENCAST =
            "org.lineageos.recorder.screen.ACTION_START_SCREENCAST";
    public static final String ACTION_STOP_SCREENCAST =
            "org.lineageos.recorder.screen.ACTION_STOP_SCREENCAST";
    private static final String ACTION_SCAN =
            "org.lineageos.recorder.server.display.SCAN";
    private static final String ACTION_STOP_SCAN =
            "org.lineageos.recorder.server.display.STOP_SCAN";

    private static final int TOTAL_NUM_TRACKS = 1;
    private static final int VIDEO_BIT_RATE = 6000000;
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int AUDIO_BIT_RATE = 16;
    private static final int AUDIO_SAMPLE_RATE = 44100;

    public static final int NOTIFICATION_ID = 61;
    private long mStartTime;
    private Timer mTimer;
    private NotificationCompat.Builder mBuilder;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private Surface mInputSurface;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private NotificationManager mNotificationManager;
    private boolean mUseAudio;
    private File mPath;
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

    public static Intent getStartIntent(Context context, int resultCode, Intent data,
                                        boolean useAudio) {
        return new Intent(context, ScreencastService.class)
                .setAction(ACTION_START_SCREENCAST)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_USE_AUDIO, useAudio);
    }

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

        // Prepare all the output metadata
        String videoDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                .format(new Date());
        // the directory which holds all recording files
        mPath = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "ScreenRecords/ScreenRecord-" + videoDate + ".mp4");

        File recordingDir = mPath.getParentFile();
        if (recordingDir == null) {
            throw new SecurityException("Cannot access scoped Movies/ScreenRecords directory");
        }
        //noinspection ResultOfMethodCallIgnored
        recordingDir.mkdirs();
        if (!(recordingDir.exists() && recordingDir.canWrite())) {
            throw new SecurityException("Cannot write to " + recordingDir);
        }

        mMediaProjectionManager = getSystemService(MediaProjectionManager.class);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mBroadcastReceiver, filter);

        mNotificationManager = getSystemService(NotificationManager.class);

        if (mNotificationManager == null || mNotificationManager.getNotificationChannel(
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

    @Override
    public void onContentWritten(@Nullable String uri) {
        stopForeground(true);
        if (uri != null) {
            sendShareNotification(uri);
        }
    }

    private int startScreencasting(Intent intent) {
        if (hasNoAvailableSpace()) {
            Toast.makeText(this, R.string.screen_insufficient_storage,
                    Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
        }

        mStartTime = SystemClock.elapsedRealtime();
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

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        mUseAudio = intent.getBooleanExtra(EXTRA_USE_AUDIO, false);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        if (data != null) {
            mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            new Thread(this::startRecording).start();
        }
        return START_STICKY;
    }

    private void startRecording() {
        try {
            Log.d(LOGTAG, "Writing video output to: " + mPath.getAbsolutePath());

            // Set up media recorder
            mMediaRecorder = new MediaRecorder();
            if (mUseAudio) {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            // Set up video
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = getSystemService(WindowManager.class);
            wm.getDefaultDisplay().getRealMetrics(metrics);
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setVideoSize(screenWidth, screenHeight);
            mMediaRecorder.setVideoFrameRate(VIDEO_FRAME_RATE);
            mMediaRecorder.setVideoEncodingBitRate(VIDEO_BIT_RATE);

            // Set up audio
            if (mUseAudio) {
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mMediaRecorder.setAudioChannels(TOTAL_NUM_TRACKS);
                mMediaRecorder.setAudioEncodingBitRate(AUDIO_BIT_RATE);
                mMediaRecorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE);
            }

            mMediaRecorder.setOutputFile(mPath);
            mMediaRecorder.prepare();

            // Create surface
            mInputSurface = mMediaRecorder.getSurface();
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "Recording Display",
                    screenWidth,
                    screenHeight,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mInputSurface,
                    null,
                    null);

            mMediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private boolean hasNoAvailableSpace() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
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

    private void stopRecording() {
        mMediaRecorder.stop();
        mMediaRecorder.release();
        mMediaRecorder = null;
        mMediaProjection.stop();
        mMediaProjection = null;
        mInputSurface.release();
        mVirtualDisplay.release();

        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }

        MediaProviderHelper.addVideoToContentProvider(getContentResolver(), mPath, this);
    }

    private void stopCasting() {
        Utils.setStatus(getApplicationContext(), Utils.PREF_RECORDING_NOTHING);
        stopRecording();

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

    private NotificationCompat.Builder createShareNotificationBuilder(String uriStr) {
        Uri uri = Uri.parse(uriStr);
        Intent intent = new Intent(this, RecorderActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);

        PendingIntent playPIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getOpenIntent(uri, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent sharePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getShareIntent(uri, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent deletePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getDeleteIntent(this, false),
                PendingIntent.FLAG_CANCEL_CURRENT);

        long timeElapsed = SystemClock.elapsedRealtime() - mStartTime;
        LastRecordHelper.setLastItem(this, uriStr, timeElapsed, false);

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
