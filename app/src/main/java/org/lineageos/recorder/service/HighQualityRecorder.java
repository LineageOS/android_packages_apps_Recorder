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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import org.lineageos.recorder.utils.PcmConverter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class HighQualityRecorder implements SoundRecording {
    private static final String TAG = "HighQualityRecorder";
    private static final String FILE_NAME_EXTENSION_WAV = "wav";
    private static final String FILE_MIME_TYPE_WAV = "audio/wav";
    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_STEREO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_IN_BYTES = 2 * AudioRecord.getMinBufferSize(SAMPLING_RATE,
            CHANNEL_IN, FORMAT);

    private AudioRecord mRecord;
    private PcmConverter mPcmConverter;
    private Path mPath;
    private int mMaxAmplitude;

    private Thread mThread;
    private final AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private final AtomicBoolean mTrackAmplitude = new AtomicBoolean(false);

    @Override
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void startRecording(Path path) {
        mPath = path;

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(SAMPLING_RATE)
                .setChannelMask(CHANNEL_IN)
                .setEncoding(FORMAT)
                .build();

        mPcmConverter = new PcmConverter(audioFormat.getSampleRate(),
                audioFormat.getChannelCount(),
                audioFormat.getFrameSizeInBytes() * 8 / audioFormat.getChannelCount());

        mRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, audioFormat.getSampleRate(),
                audioFormat.getChannelMask(), audioFormat.getEncoding(), BUFFER_SIZE_IN_BYTES);
        mRecord.startRecording();

        mIsRecording.set(true);

        mThread = new Thread(this::recordingThreadImpl);
        mThread.start();
    }

    @Override
    public boolean stopRecording() {
        if (mRecord == null) {
            return false;
        }

        mIsRecording.set(false);

        try {
            mThread.join(1000);
        } catch (InterruptedException e) {
            // Wait at most 1 second, if we fail save the current data
        } finally {
            mPcmConverter.convertToWave(mPath, BUFFER_SIZE_IN_BYTES);
        }

        return true;
    }

    @Override
    public boolean pauseRecording() {
        if (!mIsRecording.get()) {
            return false;
        }

        mRecord.stop();
        return true;
    }

    @Override
    public boolean resumeRecording() {
        if (!mIsRecording.get()) {
            return false;
        }

        mRecord.startRecording();
        return true;
    }

    @Override
    public int getCurrentAmplitude() {
        if (!mTrackAmplitude.get()) {
            mTrackAmplitude.set(true);
        }

        int value = mMaxAmplitude;
        mMaxAmplitude = 0;
        return value;
    }

    @Override
    public String getMimeType() {
        return FILE_MIME_TYPE_WAV;
    }

    @Override
    public String getFileExtension() {
        return FILE_NAME_EXTENSION_WAV;
    }

    private void recordingThreadImpl() {
        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(mPath))) {
            final byte[] data = new byte[BUFFER_SIZE_IN_BYTES];
            while (mIsRecording.get()) {
                try {
                    int status = mRecord.read(data, 0, BUFFER_SIZE_IN_BYTES);
                    switch (status) {
                        case AudioRecord.ERROR_INVALID_OPERATION:
                        case AudioRecord.ERROR_BAD_VALUE:
                            Log.e(TAG, "Error reading audio record data");
                            mIsRecording.set(false);
                            break;
                        case AudioRecord.ERROR_DEAD_OBJECT:
                        case AudioRecord.ERROR:
                            continue;
                        default:
                            // Status indicates the number of bytes
                            if (status != 0) {
                                if (mTrackAmplitude.get()) {
                                    for (int i = 0; i < data.length; i = i + 2) {
                                        int value = data[i] & 0xff | data[i + 1] << 8;
                                        if (value < 0) {
                                            value = -value;
                                        }
                                        if (mMaxAmplitude < value) {
                                            mMaxAmplitude = value;
                                        }
                                    }
                                }
                                out.write(data, 0, BUFFER_SIZE_IN_BYTES);
                            }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write audio stream", e);
                    // Stop recording
                    mIsRecording.set(false);
                }
            }
            mRecord.stop();
            mRecord.release();
        } catch (IOException e) {
            Log.e(TAG, "Can't find output file", e);
        }
    }
}
