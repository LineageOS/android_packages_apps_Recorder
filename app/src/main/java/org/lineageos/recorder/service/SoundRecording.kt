/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.service

import android.Manifest.permission
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.nio.file.Path

interface SoundRecording {
    @RequiresPermission(permission.RECORD_AUDIO)
    @Throws(IOException::class)
    fun startRecording(path: Path)

    fun stopRecording(): Boolean

    fun pauseRecording(): Boolean

    fun resumeRecording(): Boolean

    val currentAmplitude: Int

    val mimeType: String

    val fileExtension: String
}
