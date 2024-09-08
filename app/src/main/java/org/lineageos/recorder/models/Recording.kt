/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.models

import android.net.Uri
import java.time.LocalDateTime

data class Recording(
    val uri: Uri,
    val title: String,
    val dateTime: LocalDateTime,
    val duration: Long
)
