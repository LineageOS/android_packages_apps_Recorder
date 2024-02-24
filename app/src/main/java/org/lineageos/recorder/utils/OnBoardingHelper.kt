/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import org.lineageos.recorder.R

object OnBoardingHelper {
    private const val RIPPLE_DELAY = 500L
    private const val RIPPLE_REPEAT = 4L
    private const val RIPPLE_OPEN_APP_WAIT = 1
    private const val ROTATION_DELAY = 500L
    private const val ROTATION_OPEN_APP_WAIT = 3

    fun onBoardList(context: Context, view: View) {
        val prefsManager = PreferencesManager(context)
        val appOpenTimes = prefsManager.onboardListCounter

        // Wait for the user to open the app 2 times before exposing this
        if (appOpenTimes <= RIPPLE_OPEN_APP_WAIT) {
            prefsManager.onboardListCounter = appOpenTimes + 1
        }
        if (appOpenTimes == RIPPLE_OPEN_APP_WAIT) {
            // Animate using ripple effect
            for (i in 1..RIPPLE_REPEAT) {
                Handler(Looper.getMainLooper())
                    .postDelayed(pressRipple(view), i * RIPPLE_DELAY)
            }
        }
    }

    fun onBoardSettings(context: Context, view: View) {
        val prefsManager = PreferencesManager(context)
        val appOpenTimes = prefsManager.onboardSettingsCounter

        // Wait for the user to open the app 4 times before exposing this
        if (appOpenTimes <= ROTATION_OPEN_APP_WAIT) {
            prefsManager.onboardSettingsCounter = appOpenTimes + 1
        }
        if (appOpenTimes == ROTATION_OPEN_APP_WAIT) {
            Handler(Looper.getMainLooper())
                .postDelayed(rotationAnimation(context, view), ROTATION_DELAY)
        }
    }

    private fun pressRipple(view: View) = Runnable {
        view.isPressed = true
        view.postOnAnimationDelayed({ view.isPressed = false }, RIPPLE_DELAY + 100)
    }

    private fun rotationAnimation(context: Context, view: View) = Runnable {
        val animation = AnimationUtils.loadAnimation(context, R.anim.rotation)
        view.startAnimation(animation)
    }
}
