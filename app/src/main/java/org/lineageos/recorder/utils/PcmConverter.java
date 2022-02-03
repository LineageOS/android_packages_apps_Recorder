/*
 * Copyright (C) 2017-2022 The LineageOS Project
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
package org.lineageos.recorder.utils;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class PcmConverter {
    private static final long SAMPLE_RATE = 44100;
    private static final byte RECORDER_BPP = 16;
    private static final byte CHANNELS = 1;
    private static final long BYTE_RATE = CHANNELS * SAMPLE_RATE * RECORDER_BPP / 8;
    private static final byte[] WAV_HEADER = {
            'R', 'I', 'F', 'F',
            0, 0, 0, 0, // data length placeholder
            'W', 'A', 'V', 'E',
            'f', 'm', 't', ' ', // 'fmt ' chunk
            16, // 4 bytes: size of 'fmt ' chunk
            0, 0, 0, 1, // format = 1
            0,
            CHANNELS,
            0,
            (byte) (SAMPLE_RATE & 0xff), (byte) ((SAMPLE_RATE >> 8) & 0xff), 0, 0, // sample rate
            (byte) (BYTE_RATE & 0xff), (byte) ((BYTE_RATE >> 8) & 0xff),
            (byte) ((BYTE_RATE >> 16) & 0xff), 0, // byte rate
            (byte) (CHANNELS * RECORDER_BPP / 8),  // block align
            0,
            RECORDER_BPP, // bits per sample
            0,
            'd', 'a', 't', 'a',
            0, 0, 0, 0, // audio length placeholder
    };
    private static final String TAG = "PcmConverter";

    private PcmConverter() {
    }

    public static void convertToWave(Path path, int bufferSize) {
        InputStream input = null;
        OutputStream output = null;

        final Path tmpPath = path.getParent().resolve(path.getFileName() + ".tmp");

        final byte[] data = new byte[bufferSize];

        try {
            Files.createFile(tmpPath);

            input = Files.newInputStream(path);
            output = Files.newOutputStream(tmpPath);
            long audioLength = Files.size(path);
            long dataLength = audioLength + 36;

            writeWaveHeader(output, audioLength, dataLength);
            while (input.read(data) != -1) {
                output.write(data);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to convert to wav", e);
        } finally {
            Utils.closeQuietly(input);
            Utils.closeQuietly(output);
        }

        // Now rename tmp file to output destination
        try {
            // Delete old file
            Files.delete(path);

            Files.move(tmpPath, path);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file to output destination", e);
        } finally {
            Utils.closeQuietly(input);
            Utils.closeQuietly(output);
        }
    }

    // http://stackoverflow.com/questions/4440015/java-pcm-to-wav
    private static void writeWaveHeader(OutputStream out, long audioLength,
                                        long dataLength) throws IOException {
        byte[] header = Arrays.copyOf(WAV_HEADER, WAV_HEADER.length);

        header[4] = (byte) (dataLength & 0xff);
        header[5] = (byte) ((dataLength >> 8) & 0xff);
        header[6] = (byte) ((dataLength >> 16) & 0xff);
        header[7] = (byte) ((dataLength >> 24) & 0xff);
        header[40] = (byte) (audioLength & 0xff);
        header[41] = (byte) ((audioLength >> 8) & 0xff);
        header[42] = (byte) ((audioLength >> 16) & 0xff);
        header[43] = (byte) ((audioLength >> 24) & 0xff);

        out.write(header, 0, header.length);
    }
}
