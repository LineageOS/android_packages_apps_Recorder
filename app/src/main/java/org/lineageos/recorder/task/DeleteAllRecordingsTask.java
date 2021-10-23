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
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

public final class DeleteAllRecordingsTask implements Runnable {
    @NonNull
    private final ContentResolver cr;
    @NonNull
    private final List<Uri> uris;

    public DeleteAllRecordingsTask(@NonNull ContentResolver cr, @NonNull List<Uri> uris) {
        this.cr = cr;
        this.uris = uris;
    }

    @Override
    public void run() {
        uris.forEach(uri -> cr.delete(uri, null, null));
    }
}
