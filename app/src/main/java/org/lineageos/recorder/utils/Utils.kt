/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.lineageos.recorder.service.SoundRecorderService
import java.io.Closeable
import java.io.IOException

object Utils {
    fun setFullScreen(window: Window, view: View) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            return
        }
        var flags = view.systemUiVisibility
        flags = flags or (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        view.systemUiVisibility = flags
    }

    @Suppress("deprecation")
    fun setVerticalInsets(view: View?) {
        ViewCompat.setOnApplyWindowInsetsListener(view!!) { v: View, insets: WindowInsetsCompat ->
            val systemInsets = if (Build.VERSION.SDK_INT >= 31) {
                insets.getInsets(WindowInsetsCompat.Type.systemBars())
            } else {
                insets.systemWindowInsets
            }
            v.setPadding(
                v.paddingLeft, v.paddingTop,
                v.paddingRight, systemInsets.bottom
            )
            insets
        }
    }

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
        nm.cancel(SoundRecorderService.Companion.NOTIFICATION_ID)
    }
}
