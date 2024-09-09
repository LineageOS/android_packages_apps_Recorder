/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.service

import android.Manifest.permission
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.nio.file.Path

class GoodQualityRecorder(private val context: Context) : SoundRecording {
    private var recorder: MediaRecorder? = null
    private var isPaused = false

    @RequiresPermission(permission.RECORD_AUDIO)
    @Throws(IOException::class)
    override fun startRecording(path: Path) {
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("Deprecation")
            MediaRecorder()
        }).apply {
            setOutputFile(path.toFile())
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
            start()
        }
    }

    override fun stopRecording() = recorder?.let {
        if (isPaused) {
            isPaused = false
            it.resume()
        }

        // needed to prevent app crash when starting and stopping too fast
        try {
            it.stop()
        } catch (rte: RuntimeException) {
            return false
        } finally {
            it.release()
        }

        return true
    } ?: false

    override fun pauseRecording() = recorder?.let {
        if (isPaused) {
            return false
        }

        isPaused = true

        it.pause()

        true
    } ?: false

    override fun resumeRecording() = recorder?.let {
        if (!isPaused) {
            return false
        }

        it.resume()

        isPaused = false

        true
    } ?: false

    override val currentAmplitude: Int
        get() = recorder?.maxAmplitude ?: 0

    override val fileExtension = "m4a"

    override val mimeType = "audio/mp4a"
}
