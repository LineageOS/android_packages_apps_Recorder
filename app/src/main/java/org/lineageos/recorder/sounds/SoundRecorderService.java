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
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;

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
    private static final String EXTRA_STARTED = "org.lineageos.recorder.sounds.STARTED_SOUND";
    private static final String EXTRA_STOPPED = "org.lineageos.recorder.sounds.STOPPED_SOUND";
    private static final String EXTRA_FILE = "extra_filename";

    private static final String TAG = "SoundRecorderService";
    private static final File RECORDINGS_DIR =
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "SoundRecords");
    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_DEFAULT;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE,
            CHANNEL_IN, FORMAT);
    private static final int NOTIFICATION_ID = 60;
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

    @Override
    public IBinder onBind(Intent mIntent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent mIntent, int mFlags, int mStartId) {
        if (mIntent != null) {
            if (EXTRA_STARTED.equals(mIntent.getAction())) {
                startRecording();
            } else if (EXTRA_STOPPED.equals(mIntent.getAction())) {
                stopRecording();
            }
        }

        return START_STICKY;
    }

    public void onCreate() {
        BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context mContext, Intent mIntent) {
                stopRecording();
            }
        };

        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mShutdownReceiver, mFilter);
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

        File mFile = createNewAudioFile();
        if (mFile == null) {
            return;
        }

        mFilePath = mFile.getAbsolutePath();
        String mFileName = mFile.getName().replace(EXTENSION, "");

        mRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                SAMPLING_RATE, CHANNEL_IN, FORMAT, BUFFER_SIZE);

        mData = new byte[BUFFER_SIZE];
        mRecord.startRecording();
        mStatus = RecorderStatus.RECORDING;

        startRecordingThread();
        startVisualizerThread();

        startTimer();

        Intent mIntent = new Intent(EXTRA_STARTED);
        mIntent.putExtra(EXTRA_FILE, mFileName);
        startForeground(NOTIFICATION_ID, createRecordingNotification());
    }

    public void stopRecording() {
        Log.d(TAG, "Sound recorder service stopped recording\u2026");
        if (mTask != null) {
            mTask.cancel();
            mTask = null;
        }

        if (mRecord != null) {
            mStatus = RecorderStatus.STOPPED;
            mStatus = RecorderStatus.STOPPED;
            mRecord.stop();
            mRecord.release();
            mRecord = null;
            mRecordThread = null;
            mVisualizerThread = null;
        }

        File mTmpFile = new File(mFilePath);
        if (!mTmpFile.exists()) {
            mFilePath = null;
            return;
        }

        mOutFilePath = mFilePath.replace(EXTENSION, PcmConverter.WAV_EXTENSION);
        PcmConverter.convertToWave(mOutFilePath, BUFFER_SIZE);
        File mOldFile = new File(mFilePath);
        if (mOldFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mOldFile.delete();
        }

        mStatus = RecorderStatus.STOPPED;
        Intent mIntent = new Intent(EXTRA_STOPPED);
        mIntent.putExtra(EXTRA_FILE, mOutFilePath);
        sendBroadcast(mIntent);
        stopForeground(true);
    }

    private File createNewAudioFile() {
        SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss",
                Locale.getDefault());
        File mFile = new File(RECORDINGS_DIR,
                "SoundRecord-" + mDateFormat.format(new Date()) + EXTENSION);
        if (!RECORDINGS_DIR.exists()) {
            //noinspection ResultOfMethodCallIgnored
            RECORDINGS_DIR.mkdirs();
        }
        return mFile;
    }

    private void startRecordingThread() {
        mRecordThread = new Thread(() -> {
            BufferedOutputStream mOut = null;
            try {
                mOut = new BufferedOutputStream(new FileOutputStream(mFilePath));

                while (mStatus == RecorderStatus.RECORDING) {
                    int mStatus = mRecord.read(mData, 0, mData.length);
                    if (mStatus == AudioRecord.ERROR_INVALID_OPERATION ||
                            mStatus == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Error reading audio record data");
                        return;
                    }
                    mOut.write(mData, 0, BUFFER_SIZE);

                }

                mOut.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        });
        mRecordThread.start();
    }

    private void startVisualizerThread() {
        mVisualizerThread = new Thread(() -> {
            while (isRecording()) {
                try {
                    Thread.sleep((long) 100);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }

                double mVal = 0d;
                for (int mCounter = 0; mCounter < BUFFER_SIZE - 1; mCounter++) {
                    mVal += Math.pow(mData[mCounter], 2);
                }
                mVal /= BUFFER_SIZE;
                if (mAudioListener != null) {
                    mAudioListener.onAudioLevelUpdated((int) Math.sqrt(mVal));
                }
            }
        });
        mVisualizerThread.start();
    }

    public void setAudioListener(OnAudioLevelUpdatedListener mAudioListener) {
        this.mAudioListener = mAudioListener;
    }

    private void startTimer() {
        Timer mTimer = new Timer();
        mTimerListener = (mSeconds -> {
            NotificationManager mManager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            mManager.notify(NOTIFICATION_ID, createRecordingNotification());
        });

        mTask = new TimerTask() {
            @Override
            public void run() {
                mElapsedTime++;
                if (mTimerListener != null) {
                    mTimerListener.onTimerUpdated(mElapsedTime);
                }
            }
        };
        mTimer.scheduleAtFixedRate(mTask, 1000, 1000);
    }

    private Notification createRecordingNotification() {
        Context mContext = getApplicationContext();

        SimpleDateFormat mDateFormat = new SimpleDateFormat("mm:ss", Locale.getDefault());

        Intent mIntent = new Intent(this, RecorderActivity.class);
        // Fake launcher intent to resume previous activity
        mIntent.setAction("android.intent.action.MAIN");
        mIntent.addCategory("android.intent.category.LAUNCHER");
        PendingIntent mPIntent = PendingIntent.getActivity(mContext, 0, mIntent, 0);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext)
                .setContentTitle(getString(R.string.sound_notification_title))
                .setContentText(String.format(
                        getString(R.string.sound_notification_message), mDateFormat.format(mElapsedTime * 1000)))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_action_sound_record)
                .setContentIntent(mPIntent)
                .setColor(ContextCompat.getColor(mContext, R.color.colorPrimary));

        return mBuilder.build();
    }

    public void createShareNotification() {
        Intent mShareIntent = new Intent(Intent.ACTION_SEND);
        mShareIntent.setType("audio/wav");
        Uri mFileUri = FileProvider.getUriForFile(getApplicationContext(),
                "org.lineageos.recorder.fileprovider", new File(mOutFilePath));
        mShareIntent.putExtra(Intent.EXTRA_STREAM, mFileUri);
        mShareIntent.putExtra(Intent.EXTRA_SUBJECT, new File(mOutFilePath).getName());
        Intent mChooserIntent = Intent.createChooser(mShareIntent, null);
        mChooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent mContentIntent = new Intent(Intent.ACTION_VIEW);
        mContentIntent.setDataAndType(mFileUri, "audio/wav");
        mContentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent mContentPIntent = PendingIntent.getActivity(this, 0,
                mContentIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        SimpleDateFormat mDateFormat = new SimpleDateFormat("mm:ss", Locale.getDefault());

        Notification mNotification = new NotificationCompat.Builder(this)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_action_sound_record)
                .setContentTitle(getString(R.string.sound_notification_title))
                .setContentText(getString(R.string.sound_notification_message,
                        mDateFormat.format(mElapsedTime * 1000)))
                .addAction(R.drawable.ic_share, getString(R.string.share),
                        PendingIntent.getActivity(this, 0, mChooserIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT))
                .setContentIntent(mContentPIntent)
                .build();

        NotificationManager mManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        mManager.notify(NOTIFICATION_ID, mNotification);
    }

    public enum RecorderStatus {
        STOPPED,
        RECORDING
    }

}
