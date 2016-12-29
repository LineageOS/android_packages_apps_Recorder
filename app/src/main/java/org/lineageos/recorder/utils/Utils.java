/*
 * Copyright (C) 2017 The LineageOS Project
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
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class Utils {
    public static final String PREFS = "preferences";
    public static final String KEY_RECORDING = "recording";
    public static final String PREF_RECORDING_NOTHING = "nothing";
    public static final String PREF_RECORDING_SCREEN = "screen";
    public static final String PREF_RECORDING_SOUND = "sound";

    private Utils() {}

    public static String getStatus(Context sContext) {
        SharedPreferences sPrefs = sContext.getSharedPreferences(PREFS, 0);
        return sPrefs.getString(KEY_RECORDING, PREF_RECORDING_NOTHING);
    }

    public static void setStatus(Context sContext, UiStatus sStatus) {
        String sStr = PREF_RECORDING_NOTHING;
        if (sStatus.equals(UiStatus.SOUND)) {
            sStr = PREF_RECORDING_SOUND;
        } else if (sStatus.equals(UiStatus.SCREEN)) {
            sStr = PREF_RECORDING_SCREEN;
        }

        setStatus(sContext, sStr);
    }

    public static void setStatus(Context sContext, String sStatus) {
        SharedPreferences sPrefs = sContext.getSharedPreferences(PREFS, 0);
        sPrefs.edit().putString(KEY_RECORDING, sStatus).apply();
    }

    public static boolean isRecording(Context sContext) {
        return !PREF_RECORDING_NOTHING.equals(getStatus(sContext));
    }

    public static boolean isSoundRecording(Context sContext) {
        return PREF_RECORDING_SOUND.equals(getStatus(sContext));
    }

    public static boolean isScreenRecording(Context sContext) {
        return PREF_RECORDING_SCREEN.equals(getStatus(sContext));
    }

    public static int convertDp2Px(Context mContext, int mDp) {
        DisplayMetrics mMetrics = mContext.getResources().getDisplayMetrics();
        return Math.round(mDp * mMetrics.density + 0.5f);
    }

    public enum UiStatus {
        NOTHING,
        SOUND,
        SCREEN
    }

}
