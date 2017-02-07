/*
 * Copyright (C) 2013 The CyanogenMod Project
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
package org.lineageos.recorder.screen;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Semaphore;

class RecordingDevice extends EncoderDevice {
    private static final String LOGTAG = "RecordingDevice";
    private static final File RECORDINGS_DIR =
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ScreenRecords");
    private final boolean mRecordAudio;
    private final File mPath;

    RecordingDevice(Context context, int width, int height, boolean recordAudio) {
        super(context, width, height);
        mRecordAudio = recordAudio;
        // Prepare all the output metadata
        String videoDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                .format(new Date());
        // the directory which holds all recording files
        mPath = new File(RECORDINGS_DIR, "ScreenRecord-" + videoDate + ".mp4");
    }

    /**
     * @return the path of the screen cast file.
     */
    String getRecordingFilePath() {
        return mPath.getAbsolutePath();
    }

    @Override
    protected EncoderRunnable onSurfaceCreated(MediaCodec venc) {
        return new Recorder(venc);
    }

    // thread to mux the encoded audio into the final mp4 file
    @SuppressWarnings("deprecation")
    private class AudioMuxer implements Runnable {
        final AudioRecorder audio;
        final MediaMuxer muxer;
        // the video encoder waits for the audio muxer to get ready with
        // this semaphore
        final Semaphore muxWaiter;
        int track;

        AudioMuxer(AudioRecorder audio, MediaMuxer muxer, Semaphore muxWaiter) {
            this.audio = audio;
            this.muxer = muxer;
            this.muxWaiter = muxWaiter;
        }

        @Override
        public void run() {
            try {
                if (audio.record.getState() != AudioRecord.STATE_INITIALIZED) {
                    muxer.start();
                    return;
                }
                encode();
            } catch (Exception e) {
                Log.e(LOGTAG, "Audio Muxer error", e);
            } finally {
                Log.i(LOGTAG, "AudioMuxer done");
                muxWaiter.release();
            }
        }

        void encode() {
            ByteBuffer[] outs = audio.codec.getOutputBuffers();
            boolean doneCoding = false;
            // used to rewrite the presentation timestamps into something 0 based
            long start = System.nanoTime();
            while (!doneCoding) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int bufIndex = audio.codec.dequeueOutputBuffer(info, -1);
                if (bufIndex >= 0) {
                    ByteBuffer b = outs[bufIndex];

                    info.presentationTimeUs = (System.nanoTime() - start) / 1000L;
                    muxer.writeSampleData(track, b, info);

                    audio.codec.releaseOutputBuffer(bufIndex, false);
                    doneCoding = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outs = audio.codec.getOutputBuffers();
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = audio.codec.getOutputFormat();
                    track = muxer.addTrack(newFormat);
                    muxer.start();
                    muxWaiter.release();
                }
            }
        }
    }

    // Start up an AudioRecord thread to record the mic, and feed
    // the data to an encoder.
    @SuppressWarnings("deprecation")
    private class AudioRecorder implements Runnable {
        final Recorder recorder;
        final AudioRecord record;
        final MediaFormat format;
        MediaCodec codec;

        AudioRecorder(Recorder recorder) {
            try {
                codec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            } catch (IOException e) {
                Log.wtf(LOGTAG, "Can't create encoder!", e);
            }
            format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectHE);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();

            this.recorder = recorder;

            int bufferSize = 1024 * 30;
            int minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize < minBufferSize) {
                bufferSize = ((minBufferSize / 1024) + 1) * 1024 * 2;
            }
            Log.i(LOGTAG, "AudioRecorder init");
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        }

        @Override
        public void run() {
            try {
                Log.i(LOGTAG, "AudioRecorder start");
                record.startRecording();
                encode();
            } catch (Exception e) {
                Log.e(LOGTAG, "AudioRecorder error", e);
            }
            Log.i(LOGTAG, "AudioRecorder done");
            try {
                record.stop();
                record.release();
            } catch (Exception ignored) {
            }
        }

        void encode() {
            ByteBuffer[] inputs = codec.getInputBuffers();
            while (!recorder.doneCoding) {
                int bufIndex = codec.dequeueInputBuffer(1024);
                if (bufIndex < 0)
                    continue;
                ByteBuffer b = inputs[bufIndex];
                b.clear();
                int size = record.read(b, b.capacity());
                size = size < 0 ? 0 : size;
                b.clear();
                codec.queueInputBuffer(bufIndex, 0, size, System.nanoTime() / 1000L, 0);
            }
            int bufIndex = codec.dequeueInputBuffer(-1);
            codec.queueInputBuffer(bufIndex, 0, 0, System.nanoTime() / 1000L,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
    }

    @SuppressWarnings("deprecation")
    private class Recorder extends EncoderRunnable {
        boolean doneCoding = false;

        Recorder(MediaCodec venc) {
            super(venc);
        }

        @Override
        protected void cleanup() {
            super.cleanup();
            doneCoding = true;
        }

        @Override
        public void encode() throws Exception {
            File recordingDir = mPath.getParentFile();
            //noinspection ResultOfMethodCallIgnored
            recordingDir.mkdirs();
            if (!(recordingDir.exists() && recordingDir.canWrite())) {
                throw new SecurityException("Cannot write to " + recordingDir);
            }
            MediaMuxer muxer = new MediaMuxer(mPath.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            boolean muxerStarted = false;
            int trackIndex = -1;
            Thread audioThread = null;
            AudioMuxer audioMuxer;
            AudioRecorder audio;

            ByteBuffer[] encouts = venc.getOutputBuffers();
            long start = System.nanoTime();
            while (!doneCoding) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int bufIndex = venc.dequeueOutputBuffer(info, -1);
                if (bufIndex >= 0) {
                    Log.i(LOGTAG, "Dequeued buffer " + info.presentationTimeUs);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.d(LOGTAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        info.size = 0;
                    }

                    if (!muxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    ByteBuffer b = encouts[bufIndex];

                    info.presentationTimeUs = (System.nanoTime() - start) / 1000L;
                    muxer.writeSampleData(trackIndex, b, info);

                    b.clear();
                    venc.releaseOutputBuffer(bufIndex, false);

                    doneCoding = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encouts = venc.getOutputBuffers();
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // should happen before receiving buffers, and should only happen once
                    if (muxerStarted) {
                        throw new RuntimeException("format changed twice");
                    }
                    MediaFormat newFormat = venc.getOutputFormat();
                    Log.d(LOGTAG, "encoder output format changed: " + newFormat);

                    // now that we have the Magic Goodies, start the muxer
                    trackIndex = muxer.addTrack(newFormat);
                    if (mRecordAudio) {
                        audio = new AudioRecorder(this);
                        Semaphore semaphore = new Semaphore(0);
                        audioMuxer = new AudioMuxer(audio, muxer, semaphore);
                        muxerStarted = true;
                        new Thread(audio, "AudioRecorder").start();
                        audioThread = new Thread(audioMuxer, "AudioMuxer");
                        audioThread.start();

                        semaphore.acquire();
                    } else {
                        muxer.start();
                        muxerStarted = true;
                    }
                    Log.i(LOGTAG, "Muxing");
                }
            }
            doneCoding = true;
            Log.i(LOGTAG, "Done recording");
            if (audioThread != null) {
                audioThread.join();
            }
            muxer.stop();
            MediaScannerConnection.scanFile(context,
                    new String[]{mPath.getAbsolutePath()}, null,
                    (path1, uri) -> Log.i(LOGTAG, "MediaScanner scanned recording " + path1));
        }
    }
}
