/*
 * Copyright (C) 2019 The LineageOS Project
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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public final class MediaProviderHelper {
    private static final String ALBUM = "Sound records";
    private static final String SOUND_REL_PATH = "Music/SoundRecords/%1$s";
    private static final String SCREEN_REL_PATH = "Movies/ScreenRecords/%1$s";

    private MediaProviderHelper() {
    }

    public static void addSoundToContentProvider(@Nullable ContentResolver cr,
                                                 @Nullable File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || cr == null || file == null) {
            return;
        }

        final String name = file.getName();
        final ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Audio.Albums.ALBUM, ALBUM);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, String.format(SOUND_REL_PATH, name));

        cr.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
    }


    public static void addVideoToContentProvider(@Nullable ContentResolver cr,
                                                 @Nullable File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || cr == null || file == null) {
            return;
        }

        final String name = file.getName();
        final ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, String.format(SCREEN_REL_PATH, name));

        cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }
}
