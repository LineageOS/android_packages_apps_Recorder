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
package org.lineageos.recorder.task;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Callable;

public final class RenameRecordingTask implements Callable<Boolean> {
    @Nullable
    private final ContentResolver cr;
    @NonNull
    private final Uri uri;
    @NonNull
    private final String newName;


    public RenameRecordingTask(@Nullable ContentResolver cr,
                               @NonNull Uri uri,
                               @NonNull String newName) {
        this.cr = cr;
        this.uri = uri;
        this.newName = newName;
    }

    @Override
    public Boolean call() {
        if (cr == null) {
            return false;
        } else {
            final ContentValues cv = new ContentValues();
            cv.put(MediaStore.Audio.Media.DISPLAY_NAME, newName);
            cv.put(MediaStore.Audio.Media.TITLE, newName);
            final int updated = cr.update(uri, cv, null, null);
            return updated == 1;
        }
    }
}
