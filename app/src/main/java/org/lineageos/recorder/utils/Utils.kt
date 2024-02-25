/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.app.NotificationManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import org.lineageos.recorder.service.SoundRecorderService
import java.io.Closeable
import java.io.IOException

object Utils {
    /**
     * Unconditionally close a `Closeable`.
     *
     *
     * Equivalent to [Closeable.close], except any exceptions will be ignored.
     * This is typically used in finally blocks.
     *
     *
     * Example code:
     * <pre>
     * Closeable closeable = null;
     * try {
     * closeable = new FileReader("foo.txt");
     * // process closeable
     * closeable.close();
     * } catch (Exception e) {
     * // error handling
     * } finally {
     * IOUtils.closeQuietly(closeable);
     * }
    </pre> *
     *
     * @param closeable the object to close, may be null or already closed
     * @since 2.0
     */
    fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (ioe: IOException) {
            // ignore
        }
    }

    fun showKeyboard(context: Context) {
        val inputMethodManager = context.getSystemService(
            InputMethodManager::class.java
        )
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    fun closeKeyboard(context: Context) {
        val inputMethodManager = context.getSystemService(
            InputMethodManager::class.java
        )
        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }

    fun cancelShareNotification(context: Context) {
        val nm = context.getSystemService(
            NotificationManager::class.java
        ) ?: return
        nm.cancel(SoundRecorderService.NOTIFICATION_ID)
    }
}
