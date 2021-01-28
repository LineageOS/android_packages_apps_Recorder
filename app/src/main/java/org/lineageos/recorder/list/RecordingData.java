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
package org.lineageos.recorder.list;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Date;

public class RecordingData {

    @NonNull
    private final Uri uri;
    @NonNull
    private final String title;
    @NonNull
    private final Date date;
    private final long duration;

    public RecordingData(@NonNull Uri uri, @NonNull String title, @NonNull Date date,
                         long duration) {
        this.uri = uri;
        this.title = title;
        this.date = date;
        this.duration = duration;
    }

    @NonNull
    public Uri getUri() {
        return uri;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public Date getDate() {
        return date;
    }

    public long getDuration() {
        return duration;
    }
}
