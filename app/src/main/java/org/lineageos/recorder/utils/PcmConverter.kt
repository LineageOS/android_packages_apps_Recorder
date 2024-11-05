/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.media.AudioFormat
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcmConverter {
    fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channelConfig: Int,
    ) {
        val channels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2

        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray())
        header.putInt(0)  // Placeholder for file size
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)  // Sub-chunk size (16 for PCM)
        header.putShort(1) // Audio format (1 for PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2) // Byte rate
        header.putShort((channels * 2).toShort())
        header.putShort(16)  // Bits per sample
        header.put("data".toByteArray())
        header.putInt(0)  // Placeholder for data size

        out.write(header.array())
    }

    fun updateWavHeader(out: FileOutputStream) {
        val fileSize = out.channel.size()
        val audioLen = fileSize - 44
        val dataLen = audioLen + 36

        // Update file size at offset 4
        val dataLenBuf = ByteBuffer.allocate(4)
        dataLenBuf.order(ByteOrder.LITTLE_ENDIAN)
        dataLenBuf.putInt(0, dataLen.toInt())
        out.channel.position(4)
        out.channel.write(dataLenBuf)

        // Update data size at offset 40
        val audioLenBuf = ByteBuffer.allocate(4)
        audioLenBuf.order(ByteOrder.LITTLE_ENDIAN)
        audioLenBuf.putInt(0, audioLen.toInt())
        out.channel.position(40)
        out.channel.write(audioLenBuf)
    }
}
