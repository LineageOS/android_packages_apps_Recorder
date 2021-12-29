/*
 * Copyright (C) 2017-2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.recorder.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;

import org.lineageos.recorder.R;

public final class OnBoardingHelper {
    private static final long RIPPLE_DELAY = 500;
    private static final long RIPPLE_REPEAT = 4;
    private static final int RIPPLE_OPEN_APP_WAIT = 1;
    private static final long ROTATION_DELAY = 500;
    private static final int ROTATION_OPEN_APP_WAIT = 3;

    private OnBoardingHelper() {
    }

    public static void onBoardList(Context context, View view) {
        final AppPreferences prefs = AppPreferences.getInstance(context);
        final int appOpenTimes = prefs.getOnboardListCounter();

        // Wait for the user to open the app 2 times before exposing this
        if (appOpenTimes <= RIPPLE_OPEN_APP_WAIT) {
            prefs.setOnboardListCounter(appOpenTimes + 1);
        }

        if (appOpenTimes == RIPPLE_OPEN_APP_WAIT) {
            // Animate using ripple effect
            for (int i = 1; i <= RIPPLE_REPEAT; i++) {
                new Handler(Looper.getMainLooper())
                        .postDelayed(pressRipple(view), i * RIPPLE_DELAY);
            }
        }
    }

    public static void onBoardSettings(Context context, View view) {
        final AppPreferences prefs = AppPreferences.getInstance(context);
        final int appOpenTimes = prefs.getOnboardSettingsCounter();

        // Wait for the user to open the app 4 times before exposing this
        if (appOpenTimes <= ROTATION_OPEN_APP_WAIT) {
            prefs.setOnboardSettingsCounter(appOpenTimes + 1);
        }

        if (appOpenTimes == ROTATION_OPEN_APP_WAIT) {
            new Handler(Looper.getMainLooper())
                    .postDelayed(rotationAnimation(context, view), ROTATION_DELAY);
        }
    }

    @NonNull
    private static Runnable pressRipple(View view) {
        return () -> {
            view.setPressed(true);
            view.postOnAnimationDelayed(() -> view.setPressed(false), RIPPLE_DELAY + 100);
        };
    }

    @NonNull
    private static Runnable rotationAnimation(Context context, View view) {
        return () -> {
            Animation animation = AnimationUtils.loadAnimation(context, R.anim.rotation);
            view.startAnimation(animation);
        };
    }
}
