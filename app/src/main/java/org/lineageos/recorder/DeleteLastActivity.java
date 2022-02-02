/*
 * Copyright (C) 2017-2021 The LineageOS Project
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
package org.lineageos.recorder;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.lineageos.recorder.task.DeleteRecordingTask;
import org.lineageos.recorder.task.TaskExecutor;
import org.lineageos.recorder.utils.AppPreferences;
import org.lineageos.recorder.utils.Utils;

public class DeleteLastActivity extends ComponentActivity {
    private TaskExecutor mTaskExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setFinishOnTouchOutside(true);

        mTaskExecutor = new TaskExecutor();
        getLifecycle().addObserver(mTaskExecutor);

        AppPreferences preferences = AppPreferences.getInstance(this);

        final Uri uri = preferences.getLastItemUri();
        if (uri == null) {
            finish();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_title)
                    .setMessage(getString(R.string.delete_recording_message))
                    .setPositiveButton(R.string.delete, (d, which) -> mTaskExecutor.runTask(
                            new DeleteRecordingTask(getContentResolver(), uri), () -> {
                                d.dismiss();
                                Utils.cancelShareNotification(this);
                                preferences.setLastItemUri(null);
                            }))
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener(d -> finish())
                    .show();
        }
    }
}
