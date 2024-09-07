/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.ext

import android.view.View
import android.view.inputmethod.InputMethodManager

private const val SHOW_REQUEST_TIMEOUT = 1000

private fun InputMethodManager.scheduleShowSoftInput(
    view: View,
    flags: Int,
    runnable: Runnable,
    showRequestTime: Long,
) {
    if (!view.hasFocus()
        || (showRequestTime + SHOW_REQUEST_TIMEOUT) <= System.currentTimeMillis()
    ) {
        return
    }

    if (showSoftInput(view, flags)) {
        return
    } else {
        view.removeCallbacks(runnable)
        view.postDelayed(runnable, 50)
    }
}

/**
 * @see InputMethodManager.showSoftInput
 */
fun InputMethodManager.scheduleShowSoftInput(view: View, flags: Int) {
    val runnable = object : Runnable {
        override fun run() {
            scheduleShowSoftInput(view, flags, this, System.currentTimeMillis())
        }
    }

    runnable.run()
}
