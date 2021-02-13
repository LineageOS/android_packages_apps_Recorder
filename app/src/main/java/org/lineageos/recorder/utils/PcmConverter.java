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
package org.lineageos.recorder.utils;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class PcmConverter {
    private static final long SAMPLE_RATE = 44100;
    private static final int RECORDER_BPP = 16;
    private static final int CHANNELS = 1;
    private static final long BYTE_RATE = CHANNELS * SAMPLE_RATE * RECORDER_BPP / 8;
    private static final String TAG = "PcmConverter";

    public static void convertToWave(File file, int bufferSize) {
        FileInputStream input = null;
        FileOutputStream output = null;

        final File tmpFile = new File(file.getPath() + ".tmp");

        final byte[] data = new byte[bufferSize];

        try {
            input = new FileInputStream(file);
            output = new FileOutputStream(tmpFile);
            long audioLength = input.getChannel().size();
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
            //noinspection ResultOfMethodCallIgnored
            file.delete();

            input = new FileInputStream(tmpFile);
            output = new FileOutputStream(file);

            while (input.read(data) != -1) {
                output.write(data);
            }

            // Delete tmp file
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file to output destination", e);
        } finally {
            Utils.closeQuietly(input);
            Utils.closeQuietly(output);
        }
    }

    // http://stackoverflow.com/questions/4440015/java-pcm-to-wav
    private static void writeWaveHeader(FileOutputStream out, long audioLength,
                                        long dataLength) throws IOException {
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (dataLength & 0xff);
        header[5] = (byte) ((dataLength >> 8) & 0xff);
        header[6] = (byte) ((dataLength >> 16) & 0xff);
        header[7] = (byte) ((dataLength >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) CHANNELS;
        header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) (0L);
        header[27] = (byte) (0L);
        header[28] = (byte) (BYTE_RATE & 0xff);
        header[29] = (byte) ((BYTE_RATE >> 8) & 0xff);
        header[30] = (byte) ((BYTE_RATE >> 16) & 0xff);
        header[31] = (byte) (0L);
        header[32] = (byte) (CHANNELS * RECORDER_BPP / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (audioLength & 0xff);
        header[41] = (byte) ((audioLength >> 8) & 0xff);
        header[42] = (byte) ((audioLength >> 16) & 0xff);
        header[43] = (byte) ((audioLength >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}
