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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.lineageos.recorder.utils.PcmConverter;
import org.lineageos.recorder.utils.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class HighQualityRecorder implements SoundRecording {
    private static final String TAG = "HighQualityRecorder";
    private static final String FILE_NAME_EXTENSION_WAV = "wav";
    private static final String FILE_MIME_TYPE_WAV = "audio/wav";
    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_DEFAULT;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE,
            CHANNEL_IN, FORMAT);

    private AudioRecord mRecord;
    private File mFile;

    private volatile byte[] mData;
    private Thread mThread;
    private final AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private final Semaphore mPauseSemaphore = new Semaphore(1);

    @Override
    public void startRecording(File file) {
        mFile = file;
        mRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                SAMPLING_RATE, CHANNEL_IN, FORMAT, BUFFER_SIZE);
        mData = new byte[BUFFER_SIZE];
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
            mThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted thread", e);
        }

        mRecord.stop();
        mRecord.release();

        PcmConverter.convertToWave(mFile, BUFFER_SIZE);
        return true;
    }

    @Override
    public boolean pauseRecording() {
        if (!mIsRecording.get()) {
            return false;
        }

        try {
            mPauseSemaphore.acquire();
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to acquire pause semaphore", e);
        }
        mRecord.stop();
        return true;
    }

    @Override
    public boolean resumeRecording() {
        if (!mIsRecording.get() || mPauseSemaphore.availablePermits() == 1) {
            return false;
        }

        mRecord.startRecording();
        mPauseSemaphore.release();
        return true;
    }

    @Override
    public int getCurrentAmplitude() {
        byte[] data = new byte[BUFFER_SIZE];
        // Make a copy so we don't lock the object too long
        System.arraycopy(mData, 0, data, 0, BUFFER_SIZE);
        double val = 0d;
        for (byte b : data) {
            val += (b * b);
        }
        val /= BUFFER_SIZE;
        return (int) (val * 10);
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
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(mFile));

            while (mIsRecording.get()) {
                mPauseSemaphore.acquireUninterruptibly();
                try {
                    int status = mRecord.read(mData, 0, BUFFER_SIZE);
                    switch (status) {
                        case AudioRecord.ERROR_INVALID_OPERATION:
                        case AudioRecord.ERROR_BAD_VALUE:
                            Log.e(TAG, "Error reading audio record data");
                            mIsRecording.set(false);
                            break;
                        default:
                            out.write(mData, 0, BUFFER_SIZE);
                            break;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write audio stream", e);
                    // Stop recording
                    mIsRecording.set(false);
                } finally {
                    mPauseSemaphore.release();
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Can't find output file", e);
        } finally {
            Utils.closeQuietly(out);
        }
    }
}
