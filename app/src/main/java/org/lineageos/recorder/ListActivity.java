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
package org.lineageos.recorder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.recorder.list.RecordingItemCallbacks;
import org.lineageos.recorder.list.RecordingsAdapter;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.MediaProviderHelper;

public class ListActivity extends AppCompatActivity implements RecordingItemCallbacks {
    private static final String TYPE_AUDIO = "audio/wav";

    private RecordingsAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        final RecyclerView listView = findViewById(R.id.list_view);
        final ProgressBar progressBar = findViewById(R.id.list_loading);
        final TextView emptyText = findViewById(R.id.list_empty);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mAdapter = new RecordingsAdapter(this);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                if (mAdapter.getItemCount() == 0) {
                    emptyText.setVisibility(View.VISIBLE);
                }
            }
        });
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(mAdapter);

        MediaProviderHelper.requestMyRecordings(getContentResolver(), list -> {
            progressBar.setVisibility(View.GONE);
            if (list.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
            } else {
                listView.setVisibility(View.VISIBLE);
                mAdapter.setData(list);
            }
        });
    }

    @Override
    public void onPlay(@NonNull Uri uri) {
        final Intent intent = LastRecordHelper.getOpenIntent(uri, TYPE_AUDIO);
        startActivityForResult(intent, 0);
    }

    @Override
    public void onShare(@NonNull Uri uri) {
        startActivity(LastRecordHelper.getShareIntent(uri, TYPE_AUDIO));
    }

    @Override
    public void onDelete(int index, @NonNull Uri uri) {
        final AlertDialog dialog = LastRecordHelper.promptFileDeletion(
                this, uri, () -> mAdapter.onDelete(index));
        dialog.show();
    }
}
