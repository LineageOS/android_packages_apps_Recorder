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

import java.time.LocalDateTime;
import java.util.Objects;

public class RecordingData {

    @NonNull
    private final Uri uri;
    @NonNull
    private final String title;
    @NonNull
    private final LocalDateTime dateTime;
    private final long duration;

    public RecordingData(@NonNull Uri uri, @NonNull String title, @NonNull LocalDateTime dateTime,
                         long duration) {
        this.uri = uri;
        this.title = title;
        this.dateTime = dateTime;
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
    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecordingData)) {
            return false;
        }
        final RecordingData that = (RecordingData) o;
        return duration == that.duration
                && uri.equals(that.uri)
                && title.equals(that.title)
                && dateTime.equals(that.dateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, title, dateTime, duration);
    }
}
