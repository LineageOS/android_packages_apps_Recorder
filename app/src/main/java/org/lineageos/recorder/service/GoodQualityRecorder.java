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
import android.media.MediaRecorder;

import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.nio.file.Path;

public class GoodQualityRecorder implements SoundRecording {
    private static final String FILE_NAME_EXTENSION_AAC = "m4a";
    private static final String FILE_MIME_TYPE_AAC = "audio/mp4a-latm";

    private MediaRecorder mRecorder = null;
    private boolean mIsPaused = false;

    @Override
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void startRecording(Path path) throws IOException {
        mRecorder = new MediaRecorder();
        mRecorder.setOutputFile(path.toFile());
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        mRecorder.prepare();
        mRecorder.start();
    }

    @Override
    public boolean stopRecording() {
        if (mRecorder == null) {
            return false;
        }

        if (mIsPaused) {
            mIsPaused = false;
            mRecorder.resume();
        }

        // needed to prevent app crash when starting and stopping too fast
        try {
            mRecorder.stop();
        } catch (RuntimeException rte) {
            return false;
        } finally {
            mRecorder.release();
        }
        return true;
    }

    @Override
    public boolean pauseRecording() {
        if (mIsPaused || mRecorder == null) {
            return false;
        }

        mIsPaused = true;
        mRecorder.pause();
        return true;
    }

    @Override
    public boolean resumeRecording() {
        if (!mIsPaused || mRecorder == null) {
            return false;
        }

        mRecorder.resume();
        mIsPaused = false;
        return true;
    }

    @Override
    public int getCurrentAmplitude() {
        return mRecorder == null ? 0 : mRecorder.getMaxAmplitude();
    }

    @Override
    public String getMimeType() {
        return FILE_MIME_TYPE_AAC;
    }

    @Override
    public String getFileExtension() {
        return FILE_NAME_EXTENSION_AAC;
    }
}
