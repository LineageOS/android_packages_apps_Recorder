/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.content.Context
import android.net.Uri

class PreferencesManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var recordInHighQuality: Boolean
        get() = preferences.getInt(PREF_RECORDING_QUALITY, 0) == 1
        set(value) {
            preferences.edit()
                .putInt(PREF_RECORDING_QUALITY, if (value) 1 else 0)
                .apply()
        }

    var tagWithLocation: Boolean
        get() = preferences.getBoolean(PREF_TAG_WITH_LOCATION, false)
        set(tagWithLocation) {
            preferences.edit()
                .putBoolean(PREF_TAG_WITH_LOCATION, tagWithLocation)
                .apply()
        }

    var onboardSettingsCounter: Int
        get() = preferences.getInt(PREF_ONBOARD_SETTINGS_COUNTER, 0)
        set(value) {
            preferences.edit()
                .putInt(PREF_ONBOARD_SETTINGS_COUNTER, value)
                .apply()
        }

    var onboardListCounter: Int
        get() = preferences.getInt(PREF_ONBOARD_SOUND_LIST_COUNTER, 0)
        set(value) {
            preferences.edit()
                .putInt(PREF_ONBOARD_SOUND_LIST_COUNTER, value)
                .apply()
        }

    var lastItemUri: Uri?
        get() {
            val uriStr = preferences.getString(PREF_LAST_SOUND, null)
            return if (uriStr == null) null else Uri.parse(uriStr)
        }
        set(value) {
            preferences.edit()
                .putString(PREF_LAST_SOUND, value?.toString())
                .apply()
        }

    companion object {
        private const val PREFS = "preferences"
        private const val PREF_TAG_WITH_LOCATION = "tag_with_location"
        private const val PREF_RECORDING_QUALITY = "recording_quality"
        private const val PREF_ONBOARD_SETTINGS_COUNTER = "onboard_settings"
        private const val PREF_ONBOARD_SOUND_LIST_COUNTER = "onboard_list"
        private const val PREF_LAST_SOUND = "sound_last_path"
    }
}
