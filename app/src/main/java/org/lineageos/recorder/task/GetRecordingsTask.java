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
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import org.lineageos.recorder.BuildConfig;
import org.lineageos.recorder.list.RecordingData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

public final class GetRecordingsTask implements Callable<List<RecordingData>> {
    private static final String[] PROJECTION = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
    };
    private static final String MY_RECORDS_SORT =
            MediaStore.Audio.Media.DATE_ADDED + " DESC";

    @Nullable
    private final ContentResolver cr;

    public GetRecordingsTask(@Nullable ContentResolver cr) {
        this.cr = cr;
    }

    @Override
    public List<RecordingData> call() {
        if (cr == null) {
            return Collections.emptyList();
        }

        final List<RecordingData> list = new ArrayList<>();
        try (Cursor cursor = cr.query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                PROJECTION,
                MediaStore.Audio.Media.OWNER_PACKAGE_NAME + "=?",
                new String[]{BuildConfig.APPLICATION_ID},
                MY_RECORDS_SORT)) {
            if (cursor.moveToFirst()) {
                do {
                    final long id = cursor.getLong(0);
                    final Uri uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    final String name = cursor.getString(1);
                    final long timeStamp = cursor.getLong(2) * 1000L;
                    // TODO: convert to LocalDateTime
                    final Date date = new Date(timeStamp);
                    final long duration = cursor.getLong(3);
                    list.add(new RecordingData(uri, name, date, duration));
                } while (cursor.moveToNext());
            }
        }
        return list;
    }
}
