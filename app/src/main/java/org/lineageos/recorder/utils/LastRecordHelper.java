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

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.Editable;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;

import org.lineageos.recorder.DialogActivity;
import org.lineageos.recorder.R;
import org.lineageos.recorder.sounds.SoundRecorderService;

import java.util.function.Consumer;

public class LastRecordHelper {
    private static final String PREFS = "preferences";
    private static final String KEY_LAST_SOUND = "sound_last_path";

    private LastRecordHelper() {
    }

    public static AlertDialog deleteFile(Context context, final Uri uri) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.delete_title)
                .setMessage(context.getString(R.string.delete_message))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    MediaProviderHelper.remove(context.getContentResolver(), uri);
                    NotificationManager nm = context.getSystemService(NotificationManager.class);
                    if (nm == null) {
                        return;
                    }

                    nm.cancel(SoundRecorderService.NOTIFICATION_ID);
                    setLastItem(context, null);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    public static AlertDialog promptFileDeletion(Context context,
                                                 final Uri uri,
                                                 Runnable onDelete) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.delete_title)
                .setMessage(context.getString(R.string.delete_message, uri))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    NotificationManager nm = context.getSystemService(NotificationManager.class);
                    if (nm == null) {
                        return;
                    }

                    nm.cancel(SoundRecorderService.NOTIFICATION_ID);
                    MediaProviderHelper.remove(context.getContentResolver(), uri);
                    onDelete.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    public static AlertDialog promptRename(Context context,
                                           String currentTitle,
                                           Consumer<String> consumer) {
        AppCompatEditText editText = new AppCompatEditText(context);
        editText.setSingleLine(true);
        editText.setText(currentTitle);

        return new AlertDialog.Builder(context)
                .setTitle(R.string.list_edit_title)
                .setView(editText)
                .setPositiveButton(R.string.list_edit_confirm, (dialog, which) -> {
                    Editable editable = editText.getText();
                    if (editable == null || editable.length() == 0) {
                        return;
                    }

                    String newTitle = editable.toString();
                    if (!newTitle.equals(currentTitle)) {
                        consumer.accept(newTitle);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    public static Intent getShareIntent(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
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
        intent.putExtra(DialogActivity.EXTRA_DELETE_LAST_RECORDING, true);
        return intent;
    }

    public static void setLastItem(Context context, String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit()
                .putString(KEY_LAST_SOUND, path)
                .apply();
    }

    public static Uri getLastItemUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String uriStr = prefs.getString(KEY_LAST_SOUND, null);
        return uriStr == null ? null : Uri.parse(uriStr);
    }
}
