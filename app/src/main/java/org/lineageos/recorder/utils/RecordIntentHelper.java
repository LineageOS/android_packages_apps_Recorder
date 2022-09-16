/*
 * Copyright (C) 2017-2022 The LineageOS Project
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
import android.net.Uri;

import androidx.annotation.NonNull;

import org.lineageos.recorder.DeleteLastActivity;

import java.util.ArrayList;

public final class RecordIntentHelper {

    private RecordIntentHelper() {
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
        return new Intent(context, DeleteLastActivity.class);
    }
}
