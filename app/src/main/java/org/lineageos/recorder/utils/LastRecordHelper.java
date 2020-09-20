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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;

import org.lineageos.recorder.DialogActivity;
import org.lineageos.recorder.R;
import org.lineageos.recorder.sounds.SoundRecorderService;

public class LastRecordHelper {
    private static final String PREFS = "preferences";
    private static final String KEY_LAST_SOUND = "sound_last_path";
    private static final String KEY_LAST_SOUND_TIME = "sound_last_duration";

    private LastRecordHelper() {
    }

    public static AlertDialog deleteFile(Context context, final Uri uri) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.delete_title)
                .setMessage(context.getString(R.string.delete_message, uri))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    MediaProviderHelper.remove(context.getContentResolver(), uri);
                    NotificationManager nm = context.getSystemService(NotificationManager.class);
                    if (nm == null) {
                        return;
                    }
                    nm.cancel(SoundRecorderService.NOTIFICATION_ID);
                    setLastItem(context, null, 0);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    public static Intent getShareIntent(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setDataAndType(uri, mimeType);
        Intent chooserIntent = Intent.createChooser(intent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooserIntent;
    }

    public static Intent getOpenIntent(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    public static Intent getDeleteIntent(Context context) {
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra(DialogActivity.EXTRA_TITLE, R.string.sound_last_title);
        intent.putExtra(DialogActivity.EXTRA_LAST_SOUND, true);
        intent.putExtra(DialogActivity.EXTRA_DELETE_LAST_RECORDING, true);
        return intent;
    }

    public static void setLastItem(Context context, String path, long duration) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit()
                .putString(KEY_LAST_SOUND, path)
                .putLong(KEY_LAST_SOUND_TIME, duration)
                .apply();
    }

    public static Uri getLastItemUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String uriStr = prefs.getString(KEY_LAST_SOUND, null);
        return uriStr == null ? null : Uri.parse(uriStr);
    }

    private static long getLastItemDuration(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getLong(KEY_LAST_SOUND_TIME, -1);
    }

    public static String getLastItemDescription(Context context) {
        return context.getString(R.string.screen_last_message,
                getLastItemDuration(context) / 1000);
    }
}
