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

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.recorder.list.RecordingData;
import org.lineageos.recorder.list.RecordingItemCallbacks;
import org.lineageos.recorder.list.RecordingsAdapter;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.MediaProviderHelper;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class ListActivity extends AppCompatActivity implements RecordingItemCallbacks {
    private static final String TYPE_AUDIO = "audio/*";

    private RecordingsAdapter mAdapter;

    private RecyclerView mListView;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list);

        final CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        mListView = findViewById(R.id.list_view);
        mProgressBar = findViewById(R.id.list_loading);
        mEmptyText = findViewById(R.id.list_empty);

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
                    mEmptyText.setVisibility(View.VISIBLE);
                }
            }
        });
        mListView.setLayoutManager(new LinearLayoutManager(this));
        mListView.setAdapter(mAdapter);

        loadRecordings();

        Utils.setFullScreen(getWindow(), coordinatorLayout);
        Utils.setVerticalInsets(mListView);
    }

    @Override
    public void onPlay(@NonNull Uri uri) {
        startActivity(LastRecordHelper.getOpenIntent(uri, TYPE_AUDIO));
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

    @Override
    public void onRename(int index, @NonNull Uri uri, @NonNull String currentTitle) {
        final AlertDialog dialog = LastRecordHelper.promptRename(
                this,
                currentTitle,
                newTitle -> MediaProviderHelper.rename(
                        getContentResolver(),
                        uri,
                        newTitle,
                        success -> {
                            if (success) {
                                mAdapter.onRename(index, newTitle);
                            }
                        })
        );
        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_list, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_delete_all);
        item.setEnabled(mAdapter.getItemCount() > 0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete_all) {
            deleteAllRecordings();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void loadRecordings() {
        MediaProviderHelper.requestMyRecordings(getContentResolver(), list -> {
            mProgressBar.setVisibility(View.GONE);
            if (list.isEmpty()) {
                List<RecordingData> listEmpty = new ArrayList<>();
                mAdapter.setData(listEmpty);
                mEmptyText.setVisibility(View.VISIBLE);
            } else {
                mListView.setVisibility(View.VISIBLE);
                mAdapter.setData(list);
            }
        });
    }

    private void deleteAllRecordings() {
        final List<RecordingData> items = mAdapter.getData();
        if (mAdapter.getItemCount() == 0 || items.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_all_title)
                .setMessage(getString(R.string.delete_all_message))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    items.forEach(item -> {
                        MediaProviderHelper.remove(this, item.getUri());
                        mAdapter.onDelete(item);
                    });
                    Utils.cancelShareNotification(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
