/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import android.net.Uri
import java.time.LocalDateTime
import java.util.Objects
import kotlin.reflect.safeCast

class RecordingData(
    val uri: Uri,
    val title: String,
    val dateTime: LocalDateTime,
    val duration: Long
) {
    override fun equals(other: Any?) = RecordingData::class.safeCast(other)?.let {
        duration == it.duration && uri == it.uri && title == it.title && dateTime == it.dateTime
    } ?: false

    override fun hashCode() = Objects.hash(uri, title, dateTime, duration)
}
