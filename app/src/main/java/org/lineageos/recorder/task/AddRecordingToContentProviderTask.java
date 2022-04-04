/*
 * Copyright (C) 2021, 2022 The LineageOS Project
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
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.Callable;

public final class AddRecordingToContentProviderTask implements Callable<Optional<String>> {
    private static final String TAG = "AddRecordingToContentProviderTask";
    private static final String ARTIST = "Recorder";
    private static final String ALBUM = "Sound records";
    private static final String PATH = "Recordings";

    @Nullable
    private final ContentResolver cr;
    @Nullable
    private final File file;
    @NonNull
    private final String mimeType;

    public AddRecordingToContentProviderTask(@Nullable ContentResolver cr,
                                             // TODO: Convert to Path
                                             @Nullable File file,
                                             @NonNull String mimeType) {
        this.cr = cr;
        this.file = file;
        this.mimeType = mimeType;
    }

    @Override
    public Optional<String> call() {
        if (cr == null || file == null) {
            return Optional.empty();
        }

        final Uri uri = cr.insert(MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY), buildCv(file));
        if (uri == null) {
            Log.e(TAG, "Failed to insert " + file.getAbsolutePath());
            return Optional.empty();
        }

        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "w", null)) {
            if (pfd == null) {
                return Optional.empty();
            }
            try (FileOutputStream oStream = new FileOutputStream(pfd.getFileDescriptor())) {
                Files.copy(file.toPath(), oStream);
            }

            final ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, values, null, null);

            if (!file.delete()) {
                Log.w(TAG, "Failed to delete tmp file");
            }
            return Optional.of(uri.toString());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write into MediaStore", e);
            return Optional.empty();
        }
    }

    private ContentValues buildCv(@NonNull File file) {
        final ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Audio.Media.TITLE, file.getName());
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Audio.Media.ARTIST, ARTIST);
        values.put(MediaStore.Audio.Media.ALBUM, ALBUM);
        values.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, PATH);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        return values;
    }
}
