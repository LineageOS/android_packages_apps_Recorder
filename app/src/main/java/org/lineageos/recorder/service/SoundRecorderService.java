/*
 * Copyright (C) 2021-2022 The LineageOS Project
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
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.GuardedBy;
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
import org.lineageos.recorder.utils.RecordIntentHelper;
import org.lineageos.recorder.utils.PreferencesManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private static final String LEGACY_MUSIC_DIR = "Sound records";

    public static final int NOTIFICATION_ID = 60;
    private static final String NOTIFICATION_CHANNEL = "soundrecorder_notification_channel";

    private NotificationManager mNotificationManager;
    private PreferencesManager mPreferencesManager;
    private TaskExecutor mTaskExecutor;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final HashMap<IBinder, RecorderClient> mClients = new HashMap<>();
    private final Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    registerClient(RecorderClient.of(msg.replyTo, msg.replyTo.getBinder()));
                    break;
                case MSG_UNREGISTER_CLIENT:
                    synchronized (mLock) {
                        unregisterClientLocked(msg.replyTo.getBinder());
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };
    private final Messenger mMessenger = new Messenger(mHandler);

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
        unregisterClients();
        mTaskExecutor.terminate(null);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
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
        }
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

    private boolean pauseRecording() {
        if (mIsPaused) {
            Log.w(TAG, "Pausing already paused recording");
        } else if (mRecorder == null) {
            Log.e(TAG, "Pausing null recorder");
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
        }
        return false;
    }

    private boolean resumeRecording() {
        if (!mIsPaused) {
            Log.w(TAG, "Resuming non-paused recording");
        } else if (mRecorder == null) {
            Log.e(TAG, "Resuming null recorder");
        } else if (mRecorder.resumeRecording()) {
            mIsPaused = false;
            startTimers();

            notifyStatus(UiStatus.RECORDING);

            mNotificationManager.notify(NOTIFICATION_ID,
                    createRecordingNotification(mElapsedTime));
            return true;
        } else {
            Log.e(TAG, "Failed to resume the recorder");
        }
        return false;
    }

    private void onRecordCompleted(String uri) {
        notifyStatus(UiStatus.READY);
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (uri != null) {
            mNotificationManager.notify(NOTIFICATION_ID,
                    createShareNotification(uri));
        }
        mRecorder = null;
    }

    private void onRecordFailed() {
        mNotificationManager.cancel(NOTIFICATION_ID);
        stopForeground(STOP_FOREGROUND_REMOVE);
        notifyStatus(UiStatus.READY);
    }

    @NonNull
    private Optional<Path> createNewAudioFile(@NonNull String fileName,
                                              @NonNull String extension) {
        final Path recordingDir;
        if (Build.VERSION.SDK_INT >= 31) {
            recordingDir = getExternalFilesDir(Environment.DIRECTORY_RECORDINGS).toPath();
        } else {
            recordingDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC).toPath()
                    .resolve(LEGACY_MUSIC_DIR);
        }
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

    private void registerClient(RecorderClient client) {
        synchronized (mLock) {
            if (unregisterClientLocked(client.token)) {
                Log.i(TAG, "Client was already registered, override it.");
            }
            mClients.put(client.token, client);
        }
        try {
            client.token.linkToDeath(new RecorderClientDeathRecipient(this, client), 0);

            // Notify about the current status
            final int currentStatus = mRecorder == null ? UiStatus.READY
                    : mIsPaused ? UiStatus.PAUSED : UiStatus.RECORDING;

            client.send(mHandler.obtainMessage(MSG_UI_STATUS, currentStatus));

            // Also notify about elapsed time
            if (currentStatus != UiStatus.READY) {
                client.send(mHandler.obtainMessage(MSG_TIME_ELAPSED, mElapsedTime));
            }
        } catch (RemoteException ignored) {
            // Already gone
        }
    }

    private void unregisterClients() {
        synchronized (mLock) {
            for (final RecorderClient client : mClients.values()) {
                client.token.unlinkToDeath(client.deathRecipient, 0);
            }
        }
    }

    @GuardedBy("mLock")
    private boolean unregisterClientLocked(IBinder token) {
        final RecorderClient client = mClients.remove(token);
        if (client == null) {
            return false;
        }

        token.unlinkToDeath(client.deathRecipient, 0);
        return true;
    }

    private void notifyStatus(@UiStatus int newStatus) {
        notifyClients(MSG_UI_STATUS, newStatus);
    }

    private void notifyCurrentSoundAmplitude(int amplitude) {
        notifyClients(MSG_SOUND_AMPLITUDE, amplitude);
    }

    private void notifyElapsedTime(long seconds) {
        notifyClients(MSG_TIME_ELAPSED, seconds);
    }

    private void notifyClients(int what, @NonNull Object obj) {
        final List<RecorderClient> clients;
        synchronized (mLock) {
            clients = new ArrayList<>(mClients.values());
        }
        for (final RecorderClient client : clients) {
            client.send(mHandler.obtainMessage(what, obj));
        }
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
                .setContentText(getString(R.string.sound_notification_message, duration))
                .setSmallIcon(R.drawable.ic_notification_sound)
                .setColor(ContextCompat.getColor(this, R.color.colorAccent))
                .setContentIntent(pi);

        if (mIsPaused) {
            PendingIntent resumePIntent = PendingIntent.getService(this, 0,
                    new Intent(this, SoundRecorderService.class)
                            .setAction(ACTION_RESUME),
                    PendingIntent.FLAG_IMMUTABLE);
            nb.setContentTitle(getString(R.string.sound_recording_title_paused));
            nb.addAction(R.drawable.ic_resume, getString(R.string.resume), resumePIntent);
        } else {
            PendingIntent pausePIntent = PendingIntent.getService(this, 0,
                    new Intent(this, SoundRecorderService.class)
                            .setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE);
            nb.setContentTitle(getString(R.string.sound_recording_title_working));
            nb.addAction(R.drawable.ic_pause, getString(R.string.pause), pausePIntent);
        }
        nb.addAction(R.drawable.ic_stop, getString(R.string.stop), stopPIntent);
        return nb.build();
    }

    private Notification createShareNotification(String uri) {
        Uri fileUri = Uri.parse(uri);
        mPreferencesManager.setLastItemUri(uri);
        String mimeType = mRecorder.getMimeType();

        Intent intent = new Intent(this, ListActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent playPIntent = PendingIntent.getActivity(this, 0,
                RecordIntentHelper.getOpenIntent(fileUri, mimeType),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent sharePIntent = PendingIntent.getActivity(this, 0,
                RecordIntentHelper.getShareIntent(fileUri, mimeType),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deletePIntent = PendingIntent.getActivity(this, 0,
                RecordIntentHelper.getDeleteIntent(this),
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

    public static final class RecorderClient {
        final Messenger messenger;
        final IBinder token;
        @Nullable
        IBinder.DeathRecipient deathRecipient;

        private RecorderClient(Messenger messenger, IBinder token) {
            this.messenger = messenger;
            this.token = token;
        }

        static RecorderClient of(Messenger messenger, IBinder token) {
            return new RecorderClient(messenger, token);
        }

        void send(Message message) {
            try {
                messenger.send(message);

            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send message", e);
            }
        }
    }

    private static class RecorderClientDeathRecipient implements IBinder.DeathRecipient {
        private final WeakReference<SoundRecorderService> mServiceRef;
        private final RecorderClient mClient;

        RecorderClientDeathRecipient(SoundRecorderService service, RecorderClient client) {
            mServiceRef = new WeakReference<>(service);
            mClient = client;
            mClient.deathRecipient = this;
        }

        @Override
        public void binderDied() {
            SoundRecorderService service = mServiceRef.get();
            if (service == null) {
                return;
            }
            synchronized (service.mLock) {
                service.unregisterClientLocked(mClient.token);
            }
        }
    }
}
