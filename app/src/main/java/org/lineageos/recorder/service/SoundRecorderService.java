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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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
import org.lineageos.recorder.status.UiStatus;
import org.lineageos.recorder.task.AddRecordingToContentProviderTask;
import org.lineageos.recorder.task.TaskExecutor;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.PreferencesManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class SoundRecorderService extends Service {
    private static final String TAG = "SoundRecorderService";

    public static final String ACTION_START = BuildConfig.APPLICATION_ID + ".service.START";
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".service.STOP";
    public static final String ACTION_PAUSE = BuildConfig.APPLICATION_ID + ".service.PAUSE";
    public static final String ACTION_RESUME = BuildConfig.APPLICATION_ID + ".service.RESUME";

    public static final int MSG_REGISTER_CLIENT = 0;
    public static final int MSG_UNREGISTER_CLIENT = 1;
    public static final int MSG_UI_STATUS = 2;
    public static final int MSG_SOUND_AMPLITUDE = 3;
    public static final int MSG_TIME_ELAPSED = 4;

    public static final String EXTRA_FILE_NAME = "extra_filename";

    public static final int NOTIFICATION_ID = 60;
    private static final String NOTIFICATION_CHANNEL = "soundrecorder_notification_channel";

    private NotificationManager mNotificationManager;
    private PreferencesManager mPreferencesManager;
    private TaskExecutor mTaskExecutor;

    private final List<Messenger> mClients = new ArrayList<>();
    private final Messenger mMessenger = new Messenger(new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    registerClient(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    });

    private SoundRecording mRecorder = null;
    private Path mRecordPath = null;

    private Timer mAmplitudeTimer;
    private Timer mElapsedTimeTimer;

    private boolean mIsPaused;
    private long mElapsedTime;

    private final BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopRecording();
        }
    };

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
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

        mPreferencesManager = new PreferencesManager(this);
        mTaskExecutor = new TaskExecutor();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mShutdownReceiver);
        stopTimers();
        mClients.clear();
        mTaskExecutor.terminate(null);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        } else {
            switch (intent.getAction()) {
                case ACTION_START:
                    return startRecording(intent.getStringExtra(EXTRA_FILE_NAME))
                            ? START_STICKY : START_NOT_STICKY;
                case ACTION_STOP:
                    return stopRecording() ? START_STICKY : START_NOT_STICKY;
                case ACTION_PAUSE:
                    return pauseRecording() ? START_STICKY : START_NOT_STICKY;
                case ACTION_RESUME:
                    return resumeRecording() ? START_STICKY : START_NOT_STICKY;
                default:
                    return START_NOT_STICKY;
            }
        }
    }

    private boolean startRecording(String fileName) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing permission to record audio");
            return false;
        }

        mRecorder = mPreferencesManager.getRecordInHighQuality()
                ? new HighQualityRecorder()
                : new GoodQualityRecorder();

        final Optional<Path> optPath = createNewAudioFile(fileName, mRecorder.getFileExtension());
        if (optPath.isPresent()) {
            mRecordPath = optPath.get();

            mIsPaused = false;
            mElapsedTime = 0;
            try {
                mRecorder.startRecording(mRecordPath);
            } catch (IOException e) {
                Log.e(TAG, "Error while starting the recorder", e);
                return false;
            }

            notifyStatus(UiStatus.RECORDING);
            notifyElapsedTime(0);
            startTimers();
            startForeground(NOTIFICATION_ID, createRecordingNotification(0));

            return true;
        } else {
            Log.e(TAG, "Failed to prepare output file");
            return false;
        }
    }

    private boolean stopRecording() {
        if (mRecorder == null) {
            Log.e(TAG, "Trying to stop null recorder");
            return false;
        } else {
            if (mIsPaused) {
                mIsPaused = false;
                mRecorder.resumeRecording();
            }

            stopTimers();

            boolean success = mRecorder.stopRecording();
            if (success && mRecordPath != null) {
                mTaskExecutor.runTask(new AddRecordingToContentProviderTask(
                                getContentResolver(),
                                mRecordPath,
                                mRecorder.getMimeType()),
                        this::onRecordCompleted,
                        () -> Log.e(TAG, "Failed to save recording"));
                return true;
            } else {
                onRecordFailed();
                return false;
            }
        }
    }

    private boolean pauseRecording() {
        if (mIsPaused) {
            Log.w(TAG, "Pausing already paused recording");
            return false;
        } else if (mRecorder == null) {
            Log.e(TAG, "Pausing null recorder");
            return false;
        } else if (mRecorder.pauseRecording()) {
            mIsPaused = true;
            stopTimers();

            notifyCurrentSoundAmplitude(0);
            notifyStatus(UiStatus.PAUSED);

            mNotificationManager.notify(NOTIFICATION_ID,
                    createRecordingNotification(mElapsedTime));
            return true;
        } else {
            Log.e(TAG, "Failed to pause the recorder");
            return false;
        }
    }

    private boolean resumeRecording() {
        if (!mIsPaused) {
            Log.w(TAG, "Resuming non-paused recording");
            return false;
        } else if (mRecorder == null) {
            Log.e(TAG, "Resuming null recorder");
            return false;
        } else if (mRecorder.resumeRecording()) {
            mIsPaused = false;
            startTimers();

            notifyStatus(UiStatus.RECORDING);

            mNotificationManager.notify(NOTIFICATION_ID,
                    createRecordingNotification(mElapsedTime));
            return true;
        } else {
            Log.e(TAG, "Failed to resume the recorder");
            return false;
        }
    }

    private void onRecordCompleted(String uri) {
        notifyStatus(UiStatus.READY);
        stopForeground(true);
        if (uri != null) {
            mNotificationManager.notify(NOTIFICATION_ID,
                    createShareNotification(uri));
        }
        mRecorder = null;
    }

    private void onRecordFailed() {
        mNotificationManager.cancel(NOTIFICATION_ID);
        stopForeground(true);
        notifyStatus(UiStatus.READY);
    }

    @NonNull
    private Optional<Path> createNewAudioFile(@NonNull String fileName,
                                              @NonNull String extension) {
        final Path recordingDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC).toPath();
        final Path path = recordingDir.resolve(String.format(fileName, extension));
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

    /* Timers */

    private void startTimers() {
        startElapsedTimeTimer();
        startAmplitudeTimer();
    }

    private void startElapsedTimeTimer() {
        mElapsedTimeTimer = new Timer();
        mElapsedTimeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final long newElapsedTime = ++mElapsedTime;
                notifyElapsedTime(newElapsedTime);
                Notification notification = createRecordingNotification(newElapsedTime);
                mNotificationManager.notify(NOTIFICATION_ID, notification);
            }
        }, 1000, 1000);
    }

    private void startAmplitudeTimer() {
        mAmplitudeTimer = new Timer();
        mAmplitudeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                notifyCurrentSoundAmplitude(mRecorder.getCurrentAmplitude());
            }
        }, 0, 350);
    }

    private void stopTimers() {
        if (mElapsedTimeTimer != null) {
            mElapsedTimeTimer.cancel();
            mElapsedTimeTimer.purge();
            mElapsedTimeTimer = null;
        }
        if (mAmplitudeTimer != null) {
            mAmplitudeTimer.cancel();
            mAmplitudeTimer.purge();
            mAmplitudeTimer = null;
        }
    }

    /* Clients */

    private void registerClient(Messenger client) {
        mClients.add(client);

        // Notify about the current status
        final int currentStatus = mRecorder == null
                ? UiStatus.READY
                : mIsPaused ? UiStatus.PAUSED : UiStatus.RECORDING;
        try {
            client.send(Message.obtain(null, MSG_UI_STATUS, currentStatus, 0));

            // Also notify about elapsed time
            if (currentStatus != UiStatus.READY) {
                final int highBits = (int) (mElapsedTime >> 32);
                final int lowBits = (int) mElapsedTime;
                client.send(Message.obtain(null, MSG_TIME_ELAPSED, highBits, lowBits));
            }
        } catch (RemoteException ignored) {
            // Already gone
        }
    }

    private void notifyStatus(@UiStatus int newStatus) {
        mClients.forEach(client -> {
            try {
                client.send(Message.obtain(null, MSG_UI_STATUS, newStatus, 0));
            } catch (RemoteException ignored) {
            }
        });
    }

    private void notifyElapsedTime(long seconds) {
        final int highBits = (int) (seconds >> 32);
        final int lowBits = (int) seconds;
        mClients.forEach(client -> {
            try {
                client.send(Message.obtain(null, MSG_TIME_ELAPSED, highBits, lowBits));
            } catch (RemoteException ignored) {
            }
        });
    }

    private void notifyCurrentSoundAmplitude(int amplitude) {
        mClients.forEach(client -> {
            try {
                client.send(Message.obtain(null, MSG_SOUND_AMPLITUDE, amplitude, 0));
            } catch (RemoteException ignored) {
            }
        });
    }

    /* Notifications */

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.sound_channel_title);
        String description = getString(R.string.sound_channel_desc);
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL, name, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    private Notification createRecordingNotification(long elapsedTime) {
        Intent intent = new Intent(this, RecorderActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPIntent = PendingIntent.getService(this, 0,
                new Intent(this, SoundRecorderService.class)
                        .setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        String duration = DateUtils.formatElapsedTime(elapsedTime);
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setOngoing(true)
                .setContentTitle(getString(R.string.sound_notification_title))
                .setContentText(getString(R.string.sound_notification_message, duration))
                .setSmallIcon(R.drawable.ic_notification_sound)
                .setColor(ContextCompat.getColor(this, R.color.colorAccent))
                .setContentIntent(pi);

        if (mIsPaused) {
            PendingIntent resumePIntent = PendingIntent.getService(this, 0,
                    new Intent(this, SoundRecorderService.class)
                            .setAction(ACTION_RESUME),
                    PendingIntent.FLAG_IMMUTABLE);
            nb.addAction(R.drawable.ic_resume, getString(R.string.resume), resumePIntent);
        } else {
            PendingIntent pausePIntent = PendingIntent.getService(this, 0,
                    new Intent(this, SoundRecorderService.class)
                            .setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE);
            nb.addAction(R.drawable.ic_pause, getString(R.string.pause), pausePIntent);
        }
        nb.addAction(R.drawable.ic_stop, getString(R.string.stop), stopPIntent);
        return nb.build();
    }

    private Notification createShareNotification(String uri) {
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

        String duration = DateUtils.formatElapsedTime(mElapsedTime);
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
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
    }
}
