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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.lineageos.recorder.DialogActivity;
import org.lineageos.recorder.R;

import java.util.ArrayList;
import java.util.function.Consumer;

public final class LastRecordHelper {
    private static final String PREFS = "preferences";
    private static final String KEY_LAST_SOUND = "sound_last_path";

    private LastRecordHelper() {
    }

    @NonNull
    public static Intent getShareIntent(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        Intent chooserIntent = Intent.createChooser(intent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooserIntent;
    }

    @NonNull
    public static Intent getShareIntents(ArrayList<Uri> uris, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType(mimeType);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        Intent chooserIntent = Intent.createChooser(intent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooserIntent;
    }

    @NonNull
    public static Intent getOpenIntent(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    @NonNull
    public static Intent getDeleteIntent(Context context) {
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra(DialogActivity.EXTRA_TITLE, R.string.sound_last_title);
        intent.putExtra(DialogActivity.EXTRA_DELETE_LAST_RECORDING, true);
        return intent;
    }

    public static void setLastItem(@NonNull Context context, String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit()
                .putString(KEY_LAST_SOUND, path)
                .apply();
    }

    @Nullable
    public static Uri getLastItemUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String uriStr = prefs.getString(KEY_LAST_SOUND, null);
        return uriStr == null ? null : Uri.parse(uriStr);
    }
}
