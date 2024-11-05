/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.service

import android.Manifest.permission
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import org.lineageos.recorder.utils.PcmConverter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.math.abs

class HighQualityRecorder : SoundRecording {
    private var record: AudioRecord? = null
    private var file: File? = null
    private var maxAmplitude = 0
    private var isRecording = false

    @RequiresPermission(permission.RECORD_AUDIO)
    override fun startRecording(path: Path) {
        this.file = path.toFile()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLING_RATE)
            .setChannelMask(CHANNEL_IN)
            .setEncoding(FORMAT)
            .build()
        record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT, audioFormat.sampleRate,
            audioFormat.channelMask, audioFormat.encoding, BUFFER_SIZE
        ).apply {
            startRecording()
        }

        isRecording = true

        Thread { recordingThreadImpl() }.start()
    }

    override fun stopRecording(): Boolean {
        if (record == null) {
            return false
        }

        isRecording = false

        record?.stop()
        record?.release()
        record = null

        return true
    }

    override fun pauseRecording(): Boolean {
        if (!isRecording) {
            return false
        }

        record?.stop()

        return true
    }

    override fun resumeRecording(): Boolean {
        if (!isRecording) {
            return false
        }
        record?.startRecording()
        return true
    }

    override val currentAmplitude: Int
        get() {
            return maxAmplitude
        }

    private fun recordingThreadImpl() {
        try {
            FileOutputStream(file).use { out ->
                PcmConverter.writeWavHeader(out, SAMPLING_RATE, CHANNEL_IN)

                val buffer = ByteArray(BUFFER_SIZE)
                while (isRecording) {
                    val read = record?.read(buffer, 0, BUFFER_SIZE) ?: 0
                    if (read > 0) {
                        out.write(buffer, 0, read)

                        maxAmplitude = 0
                        for (i in 0 until read step 2) {
                            val sample = ByteBuffer.wrap(buffer, i, 2)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .short
                                .toInt()
                            maxAmplitude = maxOf(maxAmplitude, abs(sample))
                        }
                    }
                }

                PcmConverter.updateWavHeader(out)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Can't find output file", e)
        }
    }

    override val fileExtension = "wav"

    override val mimeType = "audio/wav"

    companion object {
        private const val TAG = "HighQualityRecorder"

        private const val SAMPLING_RATE = 44100
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_STEREO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLING_RATE,
            CHANNEL_IN,
            FORMAT
        )
    }
}
