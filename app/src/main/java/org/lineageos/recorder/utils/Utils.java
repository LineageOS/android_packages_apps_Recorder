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

public class Utils {
    public static final String PREFS = "preferences";
    public static final String KEY_RECORDING = "recording";
    public static final String PREF_RECORDING_NOTHING = "nothing";
    private static final String PREF_RECORDING_SOUND = "sound";
    private static final String PREF_RECORDING_PAUSED = "paused";
    public static final String PREF_TAG_WITH_LOCATION = "tag_with_location";
    public static final String PREF_RECORDING_QUALITY = "recording_quality";

    private Utils() {
    }

    private static String getStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getString(KEY_RECORDING, PREF_RECORDING_NOTHING);
    }

    public static void setStatus(Context context, UiStatus status) {
        switch (status) {
            case SOUND:
                setStatus(context, PREF_RECORDING_SOUND);
                break;
            case PAUSED:
                setStatus(context, PREF_RECORDING_PAUSED);
                break;
            default:
                setStatus(context, PREF_RECORDING_NOTHING);
                break;
        }
    }

    public static void setStatus(Context context, String status) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit().putString(KEY_RECORDING, status).apply();
    }

    public static boolean isRecording(Context context) {
        return !PREF_RECORDING_NOTHING.equals(getStatus(context));
    }

    public static boolean isPaused(Context context) {
        return PREF_RECORDING_PAUSED.equals(getStatus(context));
    }

    public static void setRecordingHighQuality(Context context, boolean highQuality) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit().putInt(PREF_RECORDING_QUALITY, highQuality ? 1 : 0).apply();
    }

    public static boolean getRecordInHighQuality(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getInt(PREF_RECORDING_QUALITY, 0) == 1;
    }

    public enum UiStatus {
        NOTHING,
        SOUND,
        PAUSED,
    }
}
