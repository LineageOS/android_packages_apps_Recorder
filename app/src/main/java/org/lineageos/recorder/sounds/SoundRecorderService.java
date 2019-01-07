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
package org.lineageos.recorder.sounds;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.text.format.DateUtils;
import android.util.Log;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class SoundRecorderService extends Service {

    static final String EXTENSION = ".pcm";
    private static final String ACTION_STARTED = "org.lineageos.recorder.sounds.STARTED_SOUND";
    private static final String ACTION_STOPPED = "org.lineageos.recorder.sounds.STOPPED_SOUND";
    private static final String EXTRA_FILE = "extra_filename";

    private static final String SOUNDRECORDER_NOTIFICATION_CHANNEL =
            "soundrecorder_notification_channel";

    private static final String TAG = "SoundRecorderService";
    private static final File RECORDINGS_DIR =
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "SoundRecords");
    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_DEFAULT;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE,
            CHANNEL_IN, FORMAT);
    public static final int NOTIFICATION_ID = 60;
    private final IBinder mBinder = new RecorderBinder(this);
    private int mElapsedTime;
    private TimerTask mTask;
    private OnTimerUpdatedListener mTimerListener;
    private OnAudioLevelUpdatedListener mAudioListener;
    private String mFilePath;
    private String mOutFilePath;
    private AudioRecord mRecord;
    private Thread mRecordThread;
    private Thread mVisualizerThread;
    private byte[] mData;
    private RecorderStatus mStatus = RecorderStatus.STOPPED;
    private NotificationManager mNotificationManager;
    private final BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopRecording();
        }
    };

    @Override
    public IBinder onBind(Intent mIntent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STARTED.equals(intent.getAction())) {
                startRecording();
            } else if (ACTION_STOPPED.equals(intent.getAction())) {
                stopRecording();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerReceiver(mShutdownReceiver, new IntentFilter(Intent.ACTION_SHUTDOWN));

        mNotificationManager = getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                mNotificationManager == null || mNotificationManager.getNotificationChannel(
                        SOUNDRECORDER_NOTIFICATION_CHANNEL) != null) {
            return;
        }

        CharSequence name = getString(R.string.sound_channel_title);
        String description = getString(R.string.sound_channel_desc);
        NotificationChannel notificationChannel =
                new NotificationChannel(SOUNDRECORDER_NOTIFICATION_CHANNEL,
                        name, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mShutdownReceiver);
        super.onDestroy();
    }

    public boolean isRecording() {
        return mStatus == RecorderStatus.RECORDING;
    }

    public void startRecording() {
        Log.d(TAG, "Sound recorder service started recording\u2026");
        mElapsedTime = 0;

        if (mRecord != null) {
            return;
        }

        File file = createNewAudioFile();
        if (file == null) {
            return;
        }

        mFilePath = file.getAbsolutePath();
        String fileName = file.getName().replace(EXTENSION, "");

        mRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                SAMPLING_RATE, CHANNEL_IN, FORMAT, BUFFER_SIZE);

        mData = new byte[BUFFER_SIZE];
        mRecord.startRecording();
        mStatus = RecorderStatus.RECORDING;

        startRecordingThread();
        startVisualizerThread();

        startTimer();

        Intent intent = new Intent(ACTION_STARTED);
        intent.putExtra(EXTRA_FILE, fileName);
        startForeground(NOTIFICATION_ID, createRecordingNotification());
    }

    public void stopRecording() {
        Log.d(TAG, "Sound recorder service stopped recording");
        if (mTask != null) {
            mTask.cancel();
            mTask = null;
        }

        if (mRecord != null) {
            mStatus = RecorderStatus.STOPPED;
            mRecord.stop();
            mRecord.release();
            mRecord = null;
            mRecordThread = null;
            mVisualizerThread = null;
        }

        File tmpFile = new File(mFilePath);
        if (!tmpFile.exists()) {
            mFilePath = null;
            return;
        }

        mOutFilePath = mFilePath.replace(EXTENSION, PcmConverter.WAV_EXTENSION);
        PcmConverter.convertToWave(mOutFilePath, BUFFER_SIZE);
        File oldFile = new File(mFilePath);
        if (oldFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            oldFile.delete();
        }

        mStatus = RecorderStatus.STOPPED;
        Intent intent = new Intent(ACTION_STOPPED);
        intent.putExtra(EXTRA_FILE, mOutFilePath);
        sendBroadcast(intent);
        stopForeground(true);
    }

    private File createNewAudioFile() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss",
                Locale.getDefault());
        File file = new File(RECORDINGS_DIR,
                "SoundRecord-" + dateFormat.format(new Date()) + EXTENSION);
        if (!RECORDINGS_DIR.exists()) {
            //noinspection ResultOfMethodCallIgnored
            RECORDINGS_DIR.mkdirs();
        }
        return file;
    }

    private void startRecordingThread() {
        mRecordThread = new Thread(() -> {
            BufferedOutputStream out = null;
            try {
                out = new BufferedOutputStream(new FileOutputStream(mFilePath));

                while (mStatus == RecorderStatus.RECORDING) {
                    int mStatus = mRecord.read(mData, 0, mData.length);
                    if (mStatus == AudioRecord.ERROR_INVALID_OPERATION ||
                            mStatus == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Error reading audio record data");
                        return;
                    }
                    out.write(mData, 0, BUFFER_SIZE);

                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            } finally {
                Utils.closeQuietly(out);
            }
        });
        mRecordThread.start();
    }

    private void startVisualizerThread() {
        mVisualizerThread = new Thread(() -> {
            while (isRecording()) {
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }

                double val = 0d;
                for (byte mByte : mData) {
                    val += Math.pow(mByte, 2);
                }
                val /= mData.length;
                if (mAudioListener != null) {
                    mAudioListener.onAudioLevelUpdated((int) Math.sqrt(val));
                }
            }
        });
        mVisualizerThread.start();
    }

    public void setAudioListener(OnAudioLevelUpdatedListener audioListener) {
        mAudioListener = audioListener;
    }

    private void startTimer() {
        Timer timer = new Timer();
        mTimerListener = (seconds ->
                mNotificationManager.notify(NOTIFICATION_ID, createRecordingNotification()));

        mTask = new TimerTask() {
            @Override
            public void run() {
                mElapsedTime += 1000;
                if (mTimerListener != null) {
                    mTimerListener.onTimerUpdated(mElapsedTime);
                }
            }
        };
        timer.scheduleAtFixedRate(mTask, 1000, 1000);
    }

    private Notification createRecordingNotification() {
        Intent intent = new Intent(this, RecorderActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, SOUNDRECORDER_NOTIFICATION_CHANNEL)
                .setContentTitle(getString(R.string.sound_notification_title))
                .setContentText(getString(R.string.sound_notification_message,
                        DateUtils.formatElapsedTime(mElapsedTime / 1000)))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_action_sound_record)
                .setContentIntent(pi)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary));

        return builder.build();
    }

    public void createShareNotification() {
        Intent intent = new Intent(this, RecorderActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);

        PendingIntent playPIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getOpenIntent(this, mOutFilePath, "audio/wav"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent sharePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getShareIntent(this, mOutFilePath, "audio/wav"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent deletePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getDeleteIntent(this, true),
                PendingIntent.FLAG_CANCEL_CURRENT);

        LastRecordHelper.setLastItem(this, mOutFilePath, mElapsedTime, true);

        Notification notification = new NotificationCompat.Builder(
                this, SOUNDRECORDER_NOTIFICATION_CHANNEL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_action_sound_record)
                .setContentTitle(getString(R.string.sound_notification_title))
                .setContentText(getString(R.string.sound_notification_message,
                        DateUtils.formatElapsedTime(mElapsedTime / 1000)))
                .addAction(R.drawable.ic_play, getString(R.string.play), playPIntent)
                .addAction(R.drawable.ic_share, getString(R.string.share), sharePIntent)
                .addAction(R.drawable.ic_delete, getString(R.string.delete), deletePIntent)
                .setContentIntent(pi)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    public enum RecorderStatus {
        STOPPED,
        RECORDING
    }
}
