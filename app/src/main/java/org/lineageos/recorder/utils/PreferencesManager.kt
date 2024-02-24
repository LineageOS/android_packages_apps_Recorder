/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class PreferencesManager(context: Context) {
    private val mPreferences: SharedPreferences

    init {
        mPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun setRecordingHighQuality(highQuality: Boolean) {
        mPreferences.edit()
            .putInt(PREF_RECORDING_QUALITY, if (highQuality) 1 else 0)
            .apply()
    }

    val recordInHighQuality: Boolean
        get() = mPreferences.getInt(PREF_RECORDING_QUALITY, 0) == 1
    var tagWithLocation: Boolean
        get() = mPreferences.getBoolean(PREF_TAG_WITH_LOCATION, false)
        set(tagWithLocation) {
            mPreferences.edit()
                .putBoolean(PREF_TAG_WITH_LOCATION, tagWithLocation)
                .apply()
        }
    var onboardSettingsCounter: Int
        get() = mPreferences.getInt(PREF_ONBOARD_SETTINGS_COUNTER, 0)
        set(value) {
            mPreferences.edit()
                .putInt(PREF_ONBOARD_SETTINGS_COUNTER, value)
                .apply()
        }
    var onboardListCounter: Int
        get() = mPreferences.getInt(PREF_ONBOARD_SOUND_LIST_COUNTER, 0)
        set(value) {
            mPreferences.edit()
                .putInt(PREF_ONBOARD_SOUND_LIST_COUNTER, value)
                .apply()
        }

    fun setLastItemUri(path: String?) {
        mPreferences.edit()
            .putString(PREF_LAST_SOUND, path)
            .apply()
    }

    val lastItemUri: Uri?
        get() {
            val uriStr = mPreferences.getString(PREF_LAST_SOUND, null)
            return if (uriStr == null) null else Uri.parse(uriStr)
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
