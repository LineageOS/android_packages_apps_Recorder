/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.app.NotificationManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import org.lineageos.recorder.service.SoundRecorderService

object Utils {
    fun cancelShareNotification(context: Context) {
        val nm = context.getSystemService(
            NotificationManager::class.java
        ) ?: return
        nm.cancel(SoundRecorderService.NOTIFICATION_ID)
    }
}
