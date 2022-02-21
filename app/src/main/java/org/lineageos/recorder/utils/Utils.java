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

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

import org.lineageos.recorder.service.SoundRecorderService;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.Closeable;
import java.io.IOException;

public final class Utils {
    public static final String PREFS = "preferences";
    private static final String PREF_TAG_WITH_LOCATION = "tag_with_location";
    private static final String PREF_RECORDING_QUALITY = "recording_quality";

    private Utils() {
    }

    public static void setFullScreen(Window window, View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            return;
        }

        int flags = view.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        view.setSystemUiVisibility(flags);
    }

    @SuppressWarnings("deprecation")
    public static void setVerticalInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
             Insets systemInsets = Build.VERSION.SDK_INT >= 31
                    ? insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    : insets.getSystemWindowInsets();
            v.setPadding(v.getPaddingLeft(), systemInsets.top,
                    v.getPaddingRight(), systemInsets.bottom);
            return insets;
        });
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

    public static void showKeyboard(@NonNull Context context) {
        InputMethodManager inputMethodManager = context.getSystemService(InputMethodManager.class);
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    public static void closeKeyboard(@NonNull Context context) {
        InputMethodManager inputMethodManager = context.getSystemService(InputMethodManager.class);
        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }

    public static void setRecordingHighQuality(@NonNull Context context, boolean highQuality) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit().putInt(PREF_RECORDING_QUALITY, highQuality ? 1 : 0).apply();
    }

    public static boolean getRecordInHighQuality(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getInt(PREF_RECORDING_QUALITY, 0) == 1;
    }

    public static void setTagWithLocation(@NonNull Context context, boolean tagWithLocation) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit().putBoolean(PREF_TAG_WITH_LOCATION, tagWithLocation).apply();
    }

    public static boolean getTagWithLocation(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getBoolean(PREF_TAG_WITH_LOCATION, false);
    }

    public static void cancelShareNotification(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        nm.cancel(SoundRecorderService.NOTIFICATION_ID);
    }
}
