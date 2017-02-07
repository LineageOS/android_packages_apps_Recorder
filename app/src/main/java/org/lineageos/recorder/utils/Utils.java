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

public class Utils {
    public static final String PREFS = "preferences";
    public static final String KEY_RECORDING = "recording";
    public static final String PREF_RECORDING_NOTHING = "nothing";
    public static final String PREF_RECORDING_SCREEN = "screen";
    private static final String PREF_RECORDING_SOUND = "sound";

    private Utils() {
    }

    private static String getStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getString(KEY_RECORDING, PREF_RECORDING_NOTHING);
    }

    public static void setStatus(Context context, UiStatus status) {
        if (status.equals(UiStatus.SOUND)) {
            setStatus(context, PREF_RECORDING_SOUND);
        } else if (status.equals(UiStatus.SCREEN)) {
            setStatus(context, PREF_RECORDING_SCREEN);
        } else {
            setStatus(context, PREF_RECORDING_NOTHING);
        }
    }

    public static void setStatus(Context context, String status) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit().putString(KEY_RECORDING, status).apply();
    }

    public static boolean isRecording(Context context) {
        return !PREF_RECORDING_NOTHING.equals(getStatus(context));
    }

    public static boolean isSoundRecording(Context context) {
        return PREF_RECORDING_SOUND.equals(getStatus(context));
    }

    public static boolean isScreenRecording(Context context) {
        return PREF_RECORDING_SCREEN.equals(getStatus(context));
    }

    @SuppressWarnings("SameParameterValue")
    public static int convertDp2Px(Context context, int dp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * metrics.density + 0.5f);
    }

    public enum UiStatus {
        NOTHING,
        SOUND,
        SCREEN
    }

}
