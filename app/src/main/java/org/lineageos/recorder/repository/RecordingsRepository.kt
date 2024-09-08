/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.repository

import android.content.Context
import android.net.Uri
import org.lineageos.recorder.flow.RecordingsFlow

object RecordingsRepository {
    fun recordings(context: Context, uri: Uri) = RecordingsFlow(context, uri).flowData()
}
