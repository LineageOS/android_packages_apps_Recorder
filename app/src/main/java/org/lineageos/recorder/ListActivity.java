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
import android.view.ActionMode;
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

import org.lineageos.recorder.list.ListActionModeCallback;
import org.lineageos.recorder.list.RecordingData;
import org.lineageos.recorder.list.RecordingListCallbacks;
import org.lineageos.recorder.list.RecordingsAdapter;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.MediaProviderHelper;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ListActivity extends AppCompatActivity implements RecordingListCallbacks {
    private static final String TYPE_AUDIO = "audio/*";

    private RecordingsAdapter mAdapter;

    private Toolbar mToolbar;
    private RecyclerView mListView;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;

    private ActionMode mActionMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list);

        final CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator);
        mToolbar = findViewById(R.id.toolbar);
        mListView = findViewById(R.id.list_view);
        mProgressBar = findViewById(R.id.list_loading);
        mEmptyText = findViewById(R.id.list_empty);

        setSupportActionBar(mToolbar);
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
                    changeEmptyView(true);
                    endSelectionMode();
                }
            }

            @Override
            public void onChanged() {
                super.onChanged();
                changeEmptyView(mAdapter.getItemCount() == 0);
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
        MenuItem deleteAllItem = menu.findItem(R.id.action_delete_all);
        boolean hasItems = mAdapter.getItemCount() > 0;
        deleteAllItem.setEnabled(hasItems);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_delete_all) {
            deleteAllRecordings();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void startSelectionMode() {
        // Clear previous (should do nothing), but be sure
        endSelectionMode();
        // Start action mode
        mActionMode = mToolbar.startActionMode(new ListActionModeCallback(
                this::deleteSelectedRecordings,
                this::shareSelectedRecordings));
        mAdapter.enterSelectionMode();
    }

    @Override
    public void endSelectionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
        mAdapter.exitSelectionMode();
    }

    @Override
    public void onActionModeFinished(@NonNull ActionMode mode) {
        super.onActionModeFinished(mode);
        endSelectionMode();
    }

    private void loadRecordings() {
        MediaProviderHelper.requestMyRecordings(getContentResolver(), list -> {
            mProgressBar.setVisibility(View.GONE);
            mAdapter.setData(list);
        });
    }

    private void changeEmptyView(boolean isEmpty) {
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void shareSelectedRecordings() {
        final List<RecordingData> selectedItems = mAdapter.getSelected();
        if (selectedItems.isEmpty()) {
            return;
        }

        final ArrayList<Uri> uris = selectedItems.stream()
                .map(RecordingData::getUri)
                .collect(Collectors.toCollection(ArrayList::new));
        startActivity(LastRecordHelper.getShareIntents(uris, TYPE_AUDIO));
    }

    private void deleteSelectedRecordings() {
        final List<RecordingData> selectedItems = mAdapter.getSelected();
        if (selectedItems.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_selected_title)
                .setMessage(getString(R.string.delete_selected_message))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    selectedItems.forEach(item -> {
                        MediaProviderHelper.remove(this, item.getUri());
                        mAdapter.onDelete(item);
                    });
                    Utils.cancelShareNotification(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteAllRecordings() {
        final List<RecordingData> items = mAdapter.getData();
        if (items.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_all_title)
                .setMessage(getString(R.string.delete_all_message))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    items.forEach(item -> {
                        MediaProviderHelper.remove(this, item.getUri());
                    });
                    mAdapter.setData(new ArrayList<>());
                    Utils.cancelShareNotification(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
