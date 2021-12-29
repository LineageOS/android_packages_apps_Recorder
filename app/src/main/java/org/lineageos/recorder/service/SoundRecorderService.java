/*
 * Copyright (C) 2021 The LineageOS Project
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

package org.lineageos.recorder.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.lineageos.recorder.BuildConfig;
import org.lineageos.recorder.ListActivity;
import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.task.AddRecordingToContentProviderTask;
import org.lineageos.recorder.task.TaskExecutor;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

public class SoundRecorderService extends Service {
    private static final String TAG = "SoundRecorderService";

    public static final String ACTION_START = BuildConfig.APPLICATION_ID + ".service.START";
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".service.STOP";
    public static final String ACTION_PAUSE = BuildConfig.APPLICATION_ID + ".service.PAUSE";
    public static final String ACTION_RESUME = BuildConfig.APPLICATION_ID + ".service.RESUME";

    public static final String EXTRA_LOCATION = "extra_filename";
    private static final String FILE_NAME_BASE = "SoundRecords/%1$s (%2$s).%3$s";
    private static final String FILE_NAME_LOCATION_FALLBACK = "Sound record";
    private static final String FILE_NAME_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final int NOTIFICATION_ID = 60;
    private static final String NOTIFICATION_CHANNEL = "soundrecorder_notification_channel";

    private NotificationManager mNotificationManager;
    private TaskExecutor mTaskExecutor;

    private final IBinder mBinder = new RecorderBinder(this);
    private SoundRecording mRecorder = null;
    private Path mRecordPath = null;

    private TimerTask mVisualizerTask;
    @Nullable
    private IAudioVisualizer mAudioVisualizer;

    private boolean mIsPaused;
    private TimerTask mElapsedTimeTask;
    private final AtomicLong mElapsedTime = new AtomicLong();
    private final StringBuilder mSbRecycle = new StringBuilder();

    private final DateTimeFormatter mDateFormat = DateTimeFormatter.ofPattern(
            FILE_NAME_DATE_FORMAT, Locale.getDefault());
    private final BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopRecording();
        }
    };

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerReceiver(mShutdownReceiver, new IntentFilter(Intent.ACTION_SHUTDOWN));

        mNotificationManager = getSystemService(NotificationManager.class);
        if (mNotificationManager != null &&
                mNotificationManager.getNotificationChannel(NOTIFICATION_CHANNEL) == null) {
            createNotificationChannel();
        }

        mTaskExecutor = new TaskExecutor();
    }

    @Override
    public void onDestroy() {
        mTaskExecutor.terminate(null);
        unregisterReceiver(mShutdownReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_START:
                    String locationName = intent.getStringExtra(EXTRA_LOCATION);
                    return startRecording(locationName);
                case ACTION_STOP:
                    return stopRecording();
                case ACTION_PAUSE:
                    return pauseRecording();
                case ACTION_RESUME:
                    return resumeRecording();
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public void setAudioListener(@Nullable IAudioVisualizer audioVisualizer) {
        mAudioVisualizer = audioVisualizer;
    }

    private int startRecording(@Nullable String locationName) {
        boolean highQuality = Utils.getRecordInHighQuality(this);
        mRecorder = highQuality ? new HighQualityRecorder() : new GoodQualityRecorder();

        final Optional<Path> optPath = createNewAudioFile(locationName,
                mRecorder.getFileExtension());
        // No .isEmpty for android
        //noinspection SimplifyOptionalCallChains
        if (!optPath.isPresent()) {
            Log.e(TAG, "Failed to prepare output file");
            return START_NOT_STICKY;
        }
        mRecordPath = optPath.get();
        mIsPaused = false;
        mElapsedTime.set(0);

        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                mRecorder.startRecording(mRecordPath);
            } else {
                Log.e(TAG, "Missing permission to record audio");
                return START_NOT_STICKY;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while starting the media recorder", e);
            return START_NOT_STICKY;
        }

        startTimers();
        startForeground(NOTIFICATION_ID, createRecordingNotification());
        Utils.setStatus(this, Utils.UiStatus.SOUND);
        return START_STICKY;
    }

    private int stopRecording() {
        if (mRecorder == null) {
            if (Utils.isRecording(this)) {
                // Old crash?
                Utils.setStatus(this, Utils.UiStatus.NOTHING);
            }
            return START_NOT_STICKY;
        }

        if (mIsPaused) {
            mIsPaused = false;
            mRecorder.resumeRecording();
        }

        stopTimers();

        boolean success = mRecorder.stopRecording();
        if (!success || mRecordPath == null) {
            onRecordFailed();
            return START_NOT_STICKY;
        }

        mTaskExecutor.runTask(new AddRecordingToContentProviderTask(
                        getContentResolver(),
                        mRecordPath,
                        mRecorder.getMimeType()),
                this::onRecordCompleted,
                () -> Log.e(TAG, "Failed to save recording"));
        return START_STICKY;
    }

    private int pauseRecording() {
        if (mIsPaused) {
            return START_NOT_STICKY;
        }

        if (mRecorder == null) {
            if (Utils.isRecording(this)) {
                // Old crash?
                Utils.setStatus(this, Utils.UiStatus.NOTHING);
            }
            return START_NOT_STICKY;
        }

        if (mAudioVisualizer != null) {
            mAudioVisualizer.setAmplitude(0);
        }
        stopTimers();

        if (!mRecorder.pauseRecording()) {
            return START_NOT_STICKY;
        }

        mIsPaused = true;
        mNotificationManager.notify(NOTIFICATION_ID, createRecordingNotification());
        Utils.setStatus(this, Utils.UiStatus.PAUSED);

        return START_STICKY;
    }

    private int resumeRecording() {
        if (!mIsPaused) {
            return START_NOT_STICKY;
        }

        if (mRecorder == null) {
            if (!Utils.isRecording(this)) {
                // Old crash?
                Utils.setStatus(this, Utils.UiStatus.NOTHING);
            }
            return START_NOT_STICKY;
        }

        if (!mRecorder.resumeRecording()) {
            return START_NOT_STICKY;
        }

        startTimers();
        mIsPaused = false;
        mNotificationManager.notify(NOTIFICATION_ID, createRecordingNotification());
        Utils.setStatus(this, Utils.UiStatus.SOUND);

        return START_STICKY;
    }

    private void startTimers() {
        startElapsedTimeTask();
        startVisualizerTask();
    }

    private void stopTimers() {
        mElapsedTimeTask.cancel();
        mVisualizerTask.cancel();
    }

    private void onRecordCompleted(@Nullable String uri) {
        stopForeground(true);
        if (uri != null) {
            createShareNotification(uri);
        }
        Utils.setStatus(this, Utils.UiStatus.NOTHING);
    }

    private void onRecordFailed() {
        mNotificationManager.cancel(NOTIFICATION_ID);
        stopForeground(true);
        Utils.setStatus(this, Utils.UiStatus.NOTHING);
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.sound_channel_title);
        String description = getString(R.string.sound_channel_desc);
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL, name, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    @Nullable
    private Notification createRecordingNotification() {
        if (mNotificationManager == null) {
            return null;
        }

        Intent intent = new Intent(this, RecorderActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPIntent = PendingIntent.getService(this, 0,
                new Intent(this, SoundRecorderService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        String duration = DateUtils.formatElapsedTime(mSbRecycle, mElapsedTime.get());
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setOngoing(true)
                .setContentTitle(getString(R.string.sound_notification_title))
                .setContentText(getString(R.string.sound_notification_message, duration))
                .setSmallIcon(R.drawable.ic_notification_sound)
                .setColor(ContextCompat.getColor(this, R.color.colorAccent))
                .setContentIntent(pi);

        if (mIsPaused) {
            PendingIntent resumePIntent = PendingIntent.getService(this, 0,
                    new Intent(this, SoundRecorderService.class).setAction(ACTION_RESUME),
                    PendingIntent.FLAG_IMMUTABLE);
            nb.addAction(R.drawable.ic_resume, getString(R.string.resume), resumePIntent);
        } else {
            PendingIntent pausePIntent = PendingIntent.getService(this, 0,
                    new Intent(this, SoundRecorderService.class).setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE);
            nb.addAction(R.drawable.ic_pause, getString(R.string.pause), pausePIntent);
        }
        nb.addAction(R.drawable.ic_stop, getString(R.string.stop), stopPIntent);
        return nb.build();
    }

    private void createShareNotification(@Nullable String uri) {
        if (uri == null) {
            return;
        }

        Uri fileUri = Uri.parse(uri);
        LastRecordHelper.setLastItem(this, uri);
        String mimeType = mRecorder.getMimeType();

        Intent intent = new Intent(this, ListActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent playPIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getOpenIntent(fileUri, mimeType),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent sharePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getShareIntent(fileUri, mimeType),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deletePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getDeleteIntent(this),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String duration = DateUtils.formatElapsedTime(mSbRecycle, mElapsedTime.get());
        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(getString(R.string.sound_notification_title))
                .setContentText(getString(R.string.sound_notification_message, duration))
                .setSmallIcon(R.drawable.ic_notification_sound)
                .setColor(ContextCompat.getColor(this, R.color.colorAccent))
                .addAction(R.drawable.ic_play, getString(R.string.play), playPIntent)
                .addAction(R.drawable.ic_share, getString(R.string.share), sharePIntent)
                .addAction(R.drawable.ic_delete, getString(R.string.delete), deletePIntent)
                .setContentIntent(pi)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    @NonNull
    private Optional<Path> createNewAudioFile(@Nullable String locationName,
                                              @NonNull String extension) {
        final String fileName = String.format(FILE_NAME_BASE,
                locationName == null ? FILE_NAME_LOCATION_FALLBACK : locationName,
                mDateFormat.format(LocalDateTime.now()),
                extension);
        final Path recordingDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC).toPath();
        final Path path = recordingDir.resolve(fileName);
        if (!Files.exists(recordingDir)) {
            try {
                Files.createDirectories(recordingDir);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create parent directories for output");
                return Optional.empty();
            }
        }
        return Optional.of(path);
    }

    private void startElapsedTimeTask() {
        mElapsedTimeTask = new TimerTask() {
            @Override
            public void run() {
                mElapsedTime.incrementAndGet();
                Notification notification = createRecordingNotification();
                mNotificationManager.notify(NOTIFICATION_ID, notification);
            }
        };
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(mElapsedTimeTask, 1000, 1000);
    }

    private void startVisualizerTask() {
        mVisualizerTask = new TimerTask() {
            @Override
            public void run() {
                if (mAudioVisualizer != null) {
                    mAudioVisualizer.setAmplitude(mRecorder.getCurrentAmplitude());
                }
            }
        };
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(mVisualizerTask, 0, 350);
    }
}
