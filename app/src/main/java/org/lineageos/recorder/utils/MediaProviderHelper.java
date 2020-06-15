/*
 * Copyright (C) 2019-2020 The LineageOS Project
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
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public final class MediaProviderHelper {
    private static final String TAG = "MediaProviderHelper";

    private MediaProviderHelper() {
    }

    public static void addSoundToContentProvider(
            @Nullable ContentResolver cr,
            @Nullable File file,
            @NonNull OnContentWritten listener) {
        if (cr == null || file == null) {
            return;
        }

        final ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Audio.Media.TITLE, file.getName());
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav");
        values.put(MediaStore.Audio.Media.ARTIST, "Recorder");
        values.put(MediaStore.Audio.Media.ALBUM, "Sound records");
        values.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Sound records");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        final Uri uri = cr.insert(MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) {
            Log.e(TAG, "Failed to insert " + file.getAbsolutePath());
            return;
        }

        new WriterTask(file, uri, cr, listener).execute();
    }

    public static void addVideoToContentProvider(
            @Nullable ContentResolver cr,
            @Nullable File file,
            @NonNull OnContentWritten listener) {
        if (cr == null || file == null) {
            return;
        }

        final ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Video.Media.TITLE, file.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Screen records");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        final Uri uri = cr.insert(MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) {
            Log.e(TAG, "Failed to insert " + file.getAbsolutePath());
            return;
        }

        new WriterTask(file, uri, cr, listener).execute();
    }

    static void remove(@NonNull ContentResolver cr, @NonNull Uri uri) {
        cr.delete(uri, null, null);
    }

    @RequiresApi(29)
    static class WriterTask extends AsyncTask<Void, Void, String> {
        @NonNull
        private final File file;
        @NonNull
        private final Uri uri;
        @NonNull
        private final ContentResolver cr;
        @NonNull
        private final OnContentWritten listener;

        /* synthetic */ WriterTask(@NonNull File file,
                                   @NonNull Uri uri,
                                   @NonNull ContentResolver cr,
                                   @NonNull OnContentWritten listener) {
            this.file = file;
            this.uri = uri;
            this.cr = cr;
            this.listener = listener;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                final ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "w", null);
                if (pfd == null) {
                    return null;
                }

                final FileOutputStream oStream = new FileOutputStream(pfd.getFileDescriptor());
                final InputStream iStream = new FileInputStream(file);
                byte[] buf = new byte[1048576]; // 1MB at a time
                int len;
                while ((len = iStream.read(buf)) > 0) {
                    Log.d("MICHAEL", "writing another " + len + " bytes");
                    oStream.write(buf, 0, len);
                }
                oStream.close();
                pfd.close();

                final ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, values, null, null);

                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete tmp file");
                }

                return uri.toString();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write into MediaStore", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            listener.onContentWritten(s);
        }
    }

    public interface OnContentWritten {
        void onContentWritten(@Nullable String uri);
    }
}
