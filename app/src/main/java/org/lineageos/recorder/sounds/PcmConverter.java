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

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.lineageos.recorder.sounds.SoundRecorderService.EXTENSION;

class PcmConverter {
    static final String WAV_EXTENSION = ".wav";
    private static final long SAMPLE_RATE = 44100;
    private static final int RECORDER_BPP = 16;
    private static final int CHANNELS = 1;
    private static final long BYTE_RATE = RECORDER_BPP * 441000 * CHANNELS / 8;
    private static final String TAG = "PcmConverter";

    static void convertToWave(String mInputPath, int mBufferSize) {
        FileInputStream mInput;
        FileOutputStream mOutput;

        long mAudioLength;
        long mDataLength;

        byte[] mData = new byte[mBufferSize];

        try {
            Log.d(TAG, mInputPath);
            mInput = new FileInputStream(mInputPath.replace(WAV_EXTENSION, EXTENSION));
            mOutput = new FileOutputStream(mInputPath);
            mAudioLength = mInput.getChannel().size();
            mDataLength = mAudioLength + 36;

            writeWaveHeader(mOutput, mAudioLength, mDataLength);
            while (mInput.read(mData) != -1) {
                mOutput.write(mData);
            }

            mInput.close();
            mOutput.close();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    // http://stackoverflow.com/questions/4440015/java-pcm-to-wav
    private static void writeWaveHeader(FileOutputStream mOut, long mAudioLength,
                                        long mDataLength) throws IOException {
        byte[] mHeader = new byte[44];

        mHeader[0] = 'R';  // RIFF/WAVE header
        mHeader[1] = 'I';
        mHeader[2] = 'F';
        mHeader[3] = 'F';
        mHeader[4] = (byte) (mDataLength & 0xff);
        mHeader[5] = (byte) ((mDataLength >> 8) & 0xff);
        mHeader[6] = (byte) ((mDataLength >> 16) & 0xff);
        mHeader[7] = (byte) ((mDataLength >> 24) & 0xff);
        mHeader[8] = 'W';
        mHeader[9] = 'A';
        mHeader[10] = 'V';
        mHeader[11] = 'E';
        mHeader[12] = 'f';  // 'fmt ' chunk
        mHeader[13] = 'm';
        mHeader[14] = 't';
        mHeader[15] = ' ';
        mHeader[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        mHeader[17] = 0;
        mHeader[18] = 0;
        mHeader[19] = 0;
        mHeader[20] = 1;  // format = 1
        mHeader[21] = 0;
        mHeader[22] = (byte) CHANNELS;
        mHeader[23] = 0;
        mHeader[24] = (byte) (SAMPLE_RATE & 0xff);
        mHeader[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        mHeader[26] = (byte) (0L);
        mHeader[27] = (byte) (0L);
        mHeader[28] = (byte) (BYTE_RATE & 0xff);
        mHeader[29] = (byte) ((BYTE_RATE >> 8) & 0xff);
        mHeader[30] = (byte) ((BYTE_RATE >> 16) & 0xff);
        mHeader[31] = (byte) (0L);
        mHeader[32] = (byte) (2 * 16 / 8);  // block align
        mHeader[33] = 0;
        mHeader[34] = RECORDER_BPP;  // bits per sample
        mHeader[35] = 0;
        mHeader[36] = 'd';
        mHeader[37] = 'a';
        mHeader[38] = 't';
        mHeader[39] = 'a';
        mHeader[40] = (byte) (mAudioLength & 0xff);
        mHeader[41] = (byte) ((mAudioLength >> 8) & 0xff);
        mHeader[42] = (byte) ((mAudioLength >> 16) & 0xff);
        mHeader[43] = (byte) ((mAudioLength >> 24) & 0xff);

        mOut.write(mHeader, 0, 44);

    }

}
