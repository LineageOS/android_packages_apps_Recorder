/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class PcmConverter(sampleRate: Long, channels: Int, bitsPerSample: Int) {
    private val byteRate = channels * sampleRate * bitsPerSample / 8
    private val wavHeader = byteArrayOf(
        'R'.code.toByte(),
        'I'.code.toByte(),
        'F'.code.toByte(),
        'F'.code.toByte(),
        0,
        0,
        0,
        0,  // data length placeholder
        'W'.code.toByte(),
        'A'.code.toByte(),
        'V'.code.toByte(),
        'E'.code.toByte(),
        'f'.code.toByte(),
        'm'.code.toByte(),
        't'.code.toByte(),
        ' '.code.toByte(),  // 'fmt ' chunk
        16,  // 4 bytes: size of 'fmt ' chunk
        0,
        0,
        0,
        1,  // format = 1
        0,
        channels.toByte(),
        0,
        (sampleRate and 0xffL).toByte(),
        (sampleRate shr 8 and 0xffL).toByte(),
        0,
        0,
        (byteRate and 0xffL).toByte(),
        (byteRate shr 8 and 0xffL).toByte(),
        (byteRate shr 16 and 0xffL).toByte(),
        0,
        (channels * bitsPerSample / 8).toByte(),  // block align
        0,
        bitsPerSample.toByte(),  // bits per sample
        0,
        'd'.code.toByte(),
        'a'.code.toByte(),
        't'.code.toByte(),
        'a'.code.toByte(),
        0,
        0,
        0,
        0
    )

    fun convertToWave(path: Path) {
        val tmpPath = path.parent.resolve("${path.fileName}.tmp")

        try {
            Files.newInputStream(path)?.use { input ->
                Files.newOutputStream(tmpPath)?.use { output ->
                    val audioLength = Files.size(path)
                    val dataLength = audioLength + 36
                    writeWaveHeader(output, audioLength, dataLength)
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to convert to wav", e)
        }

        // Now rename tmp file to output destination
        try {
            // Delete old file
            Files.delete(path)
            Files.move(tmpPath, path)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy file to output destination", e)
        }
    }

    // http://stackoverflow.com/questions/4440015/java-pcm-to-wav
    @Throws(IOException::class)
    private fun writeWaveHeader(
        out: OutputStream, audioLength: Long,
        dataLength: Long
    ) {
        val header = wavHeader.copyOf(wavHeader.size)
        header[4] = (dataLength and 0xffL).toByte()
        header[5] = (dataLength shr 8 and 0xffL).toByte()
        header[6] = (dataLength shr 16 and 0xffL).toByte()
        header[7] = (dataLength shr 24 and 0xffL).toByte()
        header[40] = (audioLength and 0xffL).toByte()
        header[41] = (audioLength shr 8 and 0xffL).toByte()
        header[42] = (audioLength shr 16 and 0xffL).toByte()
        header[43] = (audioLength shr 24 and 0xffL).toByte()
        out.write(header, 0, header.size)
    }

    companion object {
        private const val TAG = "PcmConverter"
    }
}
