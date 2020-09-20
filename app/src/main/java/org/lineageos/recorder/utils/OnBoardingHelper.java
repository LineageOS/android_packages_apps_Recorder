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
import android.content.SharedPreferences;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.view.View;

public class OnBoardingHelper {
    private static final String ONBOARD_SOUND_LAST = "onboard_sound_last";
    private static final long RIPPLE_DELAY = 500;
    private static final long RIPPLE_REPEAT = 4;

    public static void onBoardLastItem(Context context, View view) {
        SharedPreferences prefs = getPrefs(context);
        String key = ONBOARD_SOUND_LAST;
        if (prefs.getBoolean(key, false)) {
            return;
        }

        prefs.edit().putBoolean(key, true).apply();

        // Animate using ripple effect
        for (int i = 1; i <= RIPPLE_REPEAT; i++) {
            new Handler().postDelayed(pressRipple(view), i * RIPPLE_DELAY);
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(Utils.PREFS, 0);
    }

    @NonNull
    private static Runnable pressRipple(View view) {
        return () -> {
            view.setPressed(true);
            view.postOnAnimationDelayed(() -> view.setPressed(false), RIPPLE_DELAY + 100);
        };
    }
}