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
import android.graphics.Color;
import android.util.DisplayMetrics;

import java.io.Closeable;
import java.io.IOException;

public class Utils {
    public static final String PREFS = "preferences";
    public static final String KEY_RECORDING = "recording";
    public static final String PREF_RECORDING_NOTHING = "nothing";
    public static final String PREF_RECORDING_SCREEN = "screen";
    private static final String PREF_RECORDING_SOUND = "sound";
    public static final String PREF_SCREEN_WITH_AUDIO = "screen_with_audio";

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

    public static int darkenedColor(int color) {
        int alpha = Color.alpha(color);
        int red = getDarkenedColorValue(Color.red(color));
        int green = getDarkenedColorValue(Color.green(color));
        int blue = getDarkenedColorValue(Color.blue(color));
        return Color.argb(alpha, red, green, blue);
    }

    private static int getDarkenedColorValue(int value) {
        float dark = 0.8f; // -20% lightness
        return Math.min(Math.round(value * dark), 255);
    }

    /**
     * Unconditionally close a <code>Closeable</code>.
     * <p>
     * Equivalent to {@link Closeable#close()}, except any exceptions will be ignored.
     * This is typically used in finally blocks.
     * <p>
     * Example code:
     * <pre>
     *   Closeable closeable = null;
     *   try {
     *       closeable = new FileReader("foo.txt");
     *       // process closeable
     *       closeable.close();
     *   } catch (Exception e) {
     *       // error handling
     *   } finally {
     *       IOUtils.closeQuietly(closeable);
     *   }
     * </pre>
     *
     * @param closeable the object to close, may be null or already closed
     * @since 2.0
     */
    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }


    public enum UiStatus {
        NOTHING,
        SOUND,
        SCREEN
    }

}
