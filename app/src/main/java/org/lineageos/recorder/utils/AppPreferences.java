/*
 * Copyright (C) 2021 The LineageOS Project
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

public final class AppPreferences {
    private static final String PREFS = "preferences";
    private static final String PREF_TAG_WITH_LOCATION = "tag_with_location";
    private static final String PREF_RECORDING_QUALITY = "recording_quality";
    private static final String PREF_ONBOARD_SETTINGS_COUNTER = "onboard_settings";
    private static final String PREF_ONBOARD_SOUND_LIST_COUNTER = "onboard_list";

    private static volatile AppPreferences sInstance;

    private final SharedPreferences mPreferences;

    private AppPreferences(Context context) {
        mPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static AppPreferences getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AppPreferences.class) {
                if (sInstance == null) {
                    final Context appContext = context.getApplicationContext();
                    sInstance = new AppPreferences(appContext == null ? context : appContext);
                }
            }
        }
        return sInstance;
    }

    public void setRecordingHighQuality(boolean highQuality) {
        mPreferences.edit()
                .putInt(PREF_RECORDING_QUALITY, highQuality ? 1 : 0)
                .apply();
    }

    public boolean getRecordInHighQuality() {
        return mPreferences.getInt(PREF_RECORDING_QUALITY, 0) == 1;
    }

    public void setTagWithLocation(boolean tagWithLocation) {
        mPreferences.edit()
                .putBoolean(PREF_TAG_WITH_LOCATION, tagWithLocation)
                .apply();
    }

    public boolean getTagWithLocation() {
        return mPreferences.getBoolean(PREF_TAG_WITH_LOCATION, false);
    }

    public int getOnboardSettingsCounter() {
        return mPreferences.getInt(PREF_ONBOARD_SETTINGS_COUNTER, 0);
    }

    public void setOnboardSettingsCounter(int value) {
        mPreferences.edit()
                .putInt(PREF_ONBOARD_SETTINGS_COUNTER, value)
                .apply();
    }

    public int getOnboardListCounter() {
        return mPreferences.getInt(PREF_ONBOARD_SOUND_LIST_COUNTER, 0);
    }

    public void setOnboardListCounter(int value) {
        mPreferences.edit()
                .putInt(PREF_ONBOARD_SOUND_LIST_COUNTER, value)
                .apply();
    }
}
