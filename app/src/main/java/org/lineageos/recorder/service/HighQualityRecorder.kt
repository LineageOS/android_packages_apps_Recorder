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
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

class HighQualityRecorder : SoundRecording {
    private var record: AudioRecord? = null
    private var pcmConverter: PcmConverter? = null
    private var path: Path? = null
    private var maxAmplitude = 0
    private var isRecording = false
    private var trackAmplitude = false

    @RequiresPermission(permission.RECORD_AUDIO)
    override fun startRecording(path: Path) {
        this.path = path

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLING_RATE)
            .setChannelMask(CHANNEL_IN)
            .setEncoding(FORMAT)
            .build()
        pcmConverter = PcmConverter(
            audioFormat.sampleRate.toLong(),
            audioFormat.channelCount,
            audioFormat.frameSizeInBytes * 8 / audioFormat.channelCount
        )
        record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT, audioFormat.sampleRate,
            audioFormat.channelMask, audioFormat.encoding, BUFFER_SIZE_IN_BYTES
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

        record?.stop()
        record?.release()
        record = null

        isRecording = false

        path?.also {
            pcmConverter?.convertToWave(it)
        } ?: run {
            Log.w(TAG, "Null path")
            return false
        }

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
            if (!trackAmplitude) {
                trackAmplitude = true
            }
            val value = maxAmplitude
            maxAmplitude = 0
            return value
        }

    private fun recordingThreadImpl() {
        try {
            BufferedOutputStream(Files.newOutputStream(path)).use { out ->
                val data = ByteArray(BUFFER_SIZE_IN_BYTES)
                while (isRecording) {
                    try {
                        val record = record ?: throw NullPointerException("Null record")

                        when (val status = record.read(data, 0, BUFFER_SIZE_IN_BYTES)) {
                            AudioRecord.ERROR_INVALID_OPERATION,
                            AudioRecord.ERROR_BAD_VALUE -> {
                                Log.e(TAG, "Error reading audio record data")
                                isRecording = false
                            }

                            AudioRecord.ERROR_DEAD_OBJECT,
                            AudioRecord.ERROR -> continue

                            // Status indicates the number of bytes
                            else -> if (status != 0) {
                                if (trackAmplitude) {
                                    var i = 0
                                    while (i < status) {
                                        val value = abs(
                                            data[i].toInt() or (data[i + 1].toInt() shl 8)
                                        )
                                        if (maxAmplitude < value) {
                                            maxAmplitude = value
                                        }
                                        i += 2
                                    }
                                }
                                out.write(data, 0, status)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to write audio stream", e)
                        // Stop recording
                        isRecording = false
                    } catch (e: NullPointerException) {
                        Log.e(TAG, "Null record", e)
                        // Stop recording
                        isRecording = false
                    }
                }
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
        private val BUFFER_SIZE_IN_BYTES = 2 * AudioRecord.getMinBufferSize(
            SAMPLING_RATE,
            CHANNEL_IN,
            FORMAT
        )
    }
}
