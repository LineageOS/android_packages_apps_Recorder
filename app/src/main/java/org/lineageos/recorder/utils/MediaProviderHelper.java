/*
 * Copyright (C) 2019-2021 The LineageOS Project
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
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.lineageos.recorder.BuildConfig;
import org.lineageos.recorder.list.RecordingData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class MediaProviderHelper {
    private static final String TAG = "MediaProviderHelper";

    private static final String MY_RECORDS_SORT =
            MediaStore.Audio.Media.DATE_ADDED + " DESC";

    private static final ExecutorService executor = Executors.newFixedThreadPool(1);

    private MediaProviderHelper() {
    }

    public static void addSoundToContentProvider(
            @Nullable ContentResolver cr,
            @Nullable File file,
            @NonNull OnContentWritten listener,
            @NonNull String mimeType) {
        if (cr == null || file == null) {
            return;
        }

        final ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Audio.Media.TITLE, file.getName());
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
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

        runTask(new WriterTask(cr, uri, file), listener);
    }

    public static void requestMyRecordings(@NonNull ContentResolver cr,
                                           @NonNull OnContentLoaded listener) {

        runTask(new LoaderTask(cr), listener);
    }

    public static void remove(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver cr = context.getContentResolver();
        cr.delete(uri, null, null);
    }

    public static void rename(@NonNull ContentResolver cr,
                              @NonNull Uri uri,
                              @NonNull String newName,
                              @NonNull OnContentRenamed listener) {
        runTask(new RenameTask(cr, uri, newName), listener);
    }

    private static <T> void runTask(Callable<T> callable, Consumer<T> consumer) {
        Handler handler = new Handler(Looper.getMainLooper());
        FutureTask<T> future = new FutureTask<T>(callable) {
            @Override
            protected void done() {
                try {
                    T result = get(1, TimeUnit.MINUTES);
                    handler.post(() -> consumer.accept(result));
                } catch (InterruptedException e) {
                    Log.w(TAG, e);
                } catch (ExecutionException | TimeoutException e) {
                    throw new RuntimeException("An error occurred while executing task",
                            e.getCause());
                }
            }
        };
        executor.execute(future);
    }

    @RequiresApi(29)
    static class WriterTask implements Callable<String> {
        @NonNull
        private final ContentResolver cr;
        @NonNull
        private final Uri uri;
        @NonNull
        private final File file;

        WriterTask(@NonNull ContentResolver cr,
                   @NonNull Uri uri,
                   @NonNull File file) {
            this.cr = cr;
            this.uri = uri;
            this.file = file;
        }

        @Override
        public String call() {
            try {
                final ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "w", null);
                if (pfd == null) {
                    return null;
                }

                final FileOutputStream oStream = new FileOutputStream(pfd.getFileDescriptor());
                Files.copy(file.toPath(), oStream);
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
    }

    static class LoaderTask implements Callable<List<RecordingData>> {

        @NonNull
        private final ContentResolver cr;

        LoaderTask(@NonNull ContentResolver cr) {
            this.cr = cr;
        }

        @Override
        public List<RecordingData> call() {
            final List<RecordingData> list = new ArrayList<>();

            final String[] projection = new String[] {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DURATION,
            };
            final Cursor cursor = cr.query(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    projection,
                    MediaStore.Audio.Media.OWNER_PACKAGE_NAME + "=?",
                    new String[]{ BuildConfig.APPLICATION_ID },
                    MY_RECORDS_SORT
            );

            if (cursor.moveToFirst()) {
                do {
                    final long id = cursor.getLong(0);
                    final Uri uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    final String name = cursor.getString(1);
                    final long timeStamp = cursor.getLong(2) * 1000L;
                    final Date date = new Date(timeStamp);
                    final long duration = cursor.getLong(3);
                    list.add(new RecordingData(uri, name, date, duration));
                } while(cursor.moveToNext());
            }
            cursor.close();

            return list;
        }
    }

    @RequiresApi(29)
    static class RenameTask implements Callable<Boolean> {
        @NonNull
        private final ContentResolver cr;
        @NonNull
        private final Uri uri;
        @NonNull
        private final String newName;

        RenameTask(@NonNull ContentResolver cr,
                   @NonNull Uri uri,
                   @NonNull String newName) {
            this.cr = cr;
            this.uri = uri;
            this.newName = newName;
        }

        @Override
        public Boolean call() {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Audio.Media.DISPLAY_NAME, newName);
            cv.put(MediaStore.Audio.Media.TITLE, newName);
            int updated = cr.update(uri, cv, null, null);
            return updated == 1;
        }
    }

    public interface OnContentWritten extends Consumer<String> {
    }

    public interface OnContentLoaded extends Consumer<List<RecordingData>>{
    }

    public interface OnContentRenamed extends Consumer<Boolean> {
    }
}
