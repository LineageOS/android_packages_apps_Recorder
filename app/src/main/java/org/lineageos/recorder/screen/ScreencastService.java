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
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.MediaProviderHelper;
import org.lineageos.recorder.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
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

    private static final int VIDEO_BIT_RATE = 6000000;
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int VIDEO_I_FRAME_INTERVAL = 1;
    private static final int AUDIO_BIT_RATE = 128 * 1000;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;

    public static final int NOTIFICATION_ID = 61;

    private boolean mUseAudio;
    private long mStartTime;
    private Timer mTimer;
    private NotificationCompat.Builder mBuilder;
    private NotificationManager mNotificationManager;
    private File mPath;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;

    private Surface mInputSurface;
    private VirtualDisplay mVirtualDisplay;

    private AudioRecord mAudioRecordExternal;
    private AudioRecord mAudioRecordPlayback;

    private MediaCodec mAudioExternalEncoder;
    private MediaCodec mAudioPlaybackEncoder;
    private MediaCodec mVideoEncoder;
    private MediaMuxer mMediaMuxer;

    private int mAudioExternalTrackIndex = -1;
    private int mAudioPlaybackTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private boolean mMediaMuxerStarted = false;
    private boolean mMediaMuxerStopping = false;

    private long mCurrentTimestamp = 0;

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

            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = getSystemService(WindowManager.class);
            Display display = wm.getDefaultDisplay();
            display.getRealMetrics(metrics);
            int rotation = display.getRotation();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            // Set up muxer
            mMediaMuxer = new MediaMuxer(mPath.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mMediaMuxer.setOrientationHint(rotation);

            // Set up video
            MediaFormat video = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    screenWidth, screenHeight);
            video.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            video.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE);
            video.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL);
            video.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE);
            mVideoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mVideoEncoder.configure(video, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mVideoEncoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    if (mMediaMuxerStopping) {
                        Log.d(LOGTAG, "Video ends");
                        mVideoTrackIndex = -1;
                        if (mAudioExternalTrackIndex == -1 && mAudioPlaybackTrackIndex == -1) {
                            finishRecording();
                        }
                    } else {
                        ByteBuffer buffer = codec.getOutputBuffer(index);
                        if (buffer != null) {
                            if (mMediaMuxerStarted) {
                                mMediaMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
                                mCurrentTimestamp = info.presentationTimeUs;
                            }
                            codec.releaseOutputBuffer(index, false);
                        }
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    mVideoTrackIndex = mMediaMuxer.addTrack(format);
                    Log.d(LOGTAG, "Video track ready: " + mVideoTrackIndex);
                    if ((mAudioExternalTrackIndex != -1 || mAudioPlaybackTrackIndex != -1) && mVideoTrackIndex != -1) {
                        mMediaMuxer.start();
                        mMediaMuxerStarted = true;
                        Log.d(LOGTAG, "Start output");
                    }
                }
            });

            // Setup audio defaults
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE, AUDIO_CHANNELS, AudioFormat.ENCODING_PCM_16BIT);

            // Set up external audio
            mAudioRecordExternal = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AUDIO_SAMPLE_RATE)
                            .setChannelMask(AUDIO_CHANNELS)
                            .build())
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build();
            MediaFormat audioExternal = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    mAudioRecordExternal.getSampleRate(), mAudioRecordExternal.getChannelCount());
            audioExternal.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioExternal.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            mAudioExternalEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mAudioExternalEncoder.configure(audioExternal, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioExternalEncoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    ByteBuffer buffer = codec.getInputBuffer(index);
                    if (buffer != null && mAudioRecordExternal != null) {
                        int read = mAudioRecordExternal.read(buffer, buffer.capacity(), AudioRecord.READ_NON_BLOCKING);
                        if (read >= 0) {
                            codec.queueInputBuffer(index, 0, read, 0, 0);
                        } else {
                            mAudioExternalTrackIndex = -1;
                            if (mVideoTrackIndex == -1) {
                                finishRecording();
                            }
                        }
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    if (mMediaMuxerStopping) {
                        Log.d(LOGTAG, "Audio ends");
                        mAudioExternalTrackIndex = -1;
                        if (mVideoTrackIndex == -1) {
                            finishRecording();
                        }
                    } else {
                        ByteBuffer buffer = codec.getOutputBuffer(index);
                        if (buffer != null) {
                            info.presentationTimeUs = mCurrentTimestamp;
                            if (mMediaMuxerStarted) {
                                mMediaMuxer.writeSampleData(mAudioExternalTrackIndex, buffer, info);
                            }
                            codec.releaseOutputBuffer(index, false);
                        }
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    mAudioExternalTrackIndex = mMediaMuxer.addTrack(format);
                    Log.d(LOGTAG, "Audio track ready: " + mAudioExternalTrackIndex);
                    if (mAudioExternalTrackIndex != -1 && mVideoTrackIndex != -1) {
                        mMediaMuxer.start();
                        mMediaMuxerStarted = true;
                        Log.d(LOGTAG, "Start output");
                    }
                }
            });

            // Set up audio playback
            AudioPlaybackCaptureConfiguration config =
                    new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build();
            mAudioRecordPlayback = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AUDIO_SAMPLE_RATE)
                            .setChannelMask(AUDIO_CHANNELS)
                            .build())
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build();
            MediaFormat audioPlayback = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    mAudioRecordPlayback.getSampleRate(), mAudioRecordPlayback.getChannelCount());
            audioPlayback.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioPlayback.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            mAudioPlaybackEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mAudioPlaybackEncoder.configure(audioPlayback, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioPlaybackEncoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    ByteBuffer buffer = codec.getInputBuffer(index);
                    if (buffer != null && mAudioRecordPlayback != null) {
                        int read = mAudioRecordPlayback.read(buffer, buffer.capacity(), AudioRecord.READ_NON_BLOCKING);
                        if (read >= 0) {
                            codec.queueInputBuffer(index, 0, read, 0, 0);
                        } else {
                            mAudioPlaybackTrackIndex = -1;
                            if (mVideoTrackIndex == -1) {
                                finishRecording();
                            }
                        }
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    if (mMediaMuxerStopping) {
                        Log.d(LOGTAG, "Audio ends");
                        mAudioPlaybackTrackIndex = -1;
                        if (mVideoTrackIndex == -1) {
                            finishRecording();
                        }
                    } else {
                        ByteBuffer buffer = codec.getOutputBuffer(index);
                        if (buffer != null) {
                            info.presentationTimeUs = mCurrentTimestamp;
                            if (mMediaMuxerStarted) {
                                mMediaMuxer.writeSampleData(mAudioPlaybackTrackIndex, buffer, info);
                            }
                            codec.releaseOutputBuffer(index, false);
                        }
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    mAudioPlaybackTrackIndex = mMediaMuxer.addTrack(format);
                    Log.d(LOGTAG, "Audio track ready: " + mAudioPlaybackTrackIndex);
                    if (mAudioPlaybackTrackIndex != -1 && mVideoTrackIndex != -1) {
                        mMediaMuxer.start();
                        mMediaMuxerStarted = true;
                        Log.d(LOGTAG, "Start output");
                    }
                }
            });

            // Create surface
            mInputSurface = mVideoEncoder.createInputSurface();
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "Recording Display",
                    screenWidth,
                    screenHeight,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mInputSurface,
                    null,
                    null);

            mMediaMuxerStarted = false;
            mMediaMuxerStopping = false;

            mVideoTrackIndex = -1;
            mAudioExternalTrackIndex = -1;
            mAudioPlaybackTrackIndex = -1;

            mVideoEncoder.start();
            if (mUseAudio) {
                mAudioExternalEncoder.start();
                mAudioRecordExternal.startRecording();
            }
            mAudioPlaybackEncoder.start();
            mAudioRecordPlayback.startRecording();

            mCurrentTimestamp = System.currentTimeMillis() / 100;
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
        if (mMediaMuxerStopping || mMediaProjection == null) {
            return;
        }

        mMediaProjection.stop();
        mMediaProjection = null;

        mInputSurface.release();
        mInputSurface = null;

        mVirtualDisplay.release();
        mVirtualDisplay = null;

        mMediaMuxerStopping = true;

        finishRecording();
    }

    private void finishRecording() {
        if (!mMediaMuxerStopping) {
            return;
        }

        mMediaMuxerStopping = false;

        mAudioRecordExternal.stop();
        mAudioRecordExternal.release();
        mAudioRecordExternal = null;

        mVideoEncoder.stop();
        mVideoEncoder.release();
        mVideoEncoder = null;

        mAudioExternalEncoder.stop();
        mAudioExternalEncoder.release();
        mAudioExternalEncoder = null;

        mAudioPlaybackEncoder.stop();
        mAudioPlaybackEncoder.release();
        mAudioPlaybackEncoder = null;

        mMediaMuxer.stop();
        mMediaMuxer.release();
        mMediaMuxer = null;

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
