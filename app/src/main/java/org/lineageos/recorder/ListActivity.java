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
import android.text.Editable;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
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
import org.lineageos.recorder.task.DeleteAllRecordingsTask;
import org.lineageos.recorder.task.DeleteRecordingTask;
import org.lineageos.recorder.task.GetRecordingsTask;
import org.lineageos.recorder.task.RenameRecordingTask;
import org.lineageos.recorder.task.TaskExecutor;
import org.lineageos.recorder.utils.RecordIntentHelper;
import org.lineageos.recorder.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
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

    private TaskExecutor mTaskExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTaskExecutor = new TaskExecutor();
        getLifecycle().addObserver(mTaskExecutor);

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
        });
        mListView.setLayoutManager(new LinearLayoutManager(this));
        mListView.setAdapter(mAdapter);

        loadRecordings();

        Utils.setFullScreen(getWindow(), coordinatorLayout);
        Utils.setVerticalInsets(mListView);
    }

    @Override
    public void onPlay(@NonNull Uri uri) {
        startActivity(RecordIntentHelper.getOpenIntent(uri, TYPE_AUDIO));
    }

    @Override
    public void onShare(@NonNull Uri uri) {
        startActivity(RecordIntentHelper.getShareIntent(uri, TYPE_AUDIO));
    }

    @Override
    public void onDelete(int index, @NonNull Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_recording_message))
                .setPositiveButton(R.string.delete, (d, which) -> mTaskExecutor.runTask(
                        new DeleteRecordingTask(getContentResolver(), uri), () -> {
                            mAdapter.onDelete(index);
                            Utils.cancelShareNotification(this);
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onRename(int index, @NonNull Uri uri, @NonNull String currentTitle) {
        final LayoutInflater inflater = getSystemService(LayoutInflater.class);
        final View view = inflater.inflate(R.layout.dialog_content_rename, null);
        EditText editText = view.findViewById(R.id.name);
        editText.setText(currentTitle);
        editText.requestFocus();
        Utils.showKeyboard(this);

        new AlertDialog.Builder(this)
                .setTitle(R.string.list_edit_title)
                .setView(view)
                .setPositiveButton(R.string.list_edit_confirm, (d, which) -> {
                    Editable editable = editText.getText();
                    if (editable == null || editable.length() == 0) {
                        return;
                    }

                    String newTitle = editable.toString();
                    if (!newTitle.equals(currentTitle)) {
                        renameRecording(uri, newTitle, index);
                    }
                    Utils.closeKeyboard(this);
                })
                .setNegativeButton(R.string.cancel, (d, which) -> Utils.closeKeyboard(this))
                .show();
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
            promptDeleteAllRecordings();
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
        mTaskExecutor.runTask(new GetRecordingsTask(getContentResolver()), list -> {
            mProgressBar.setVisibility(View.GONE);
            mAdapter.setData(list);
            changeEmptyView(list.isEmpty());
        });
    }

    private void renameRecording(@NonNull Uri uri, @NonNull String newTitle, int index) {
        mTaskExecutor.runTask(new RenameRecordingTask(getContentResolver(), uri, newTitle),
                success -> {
                    if (success) {
                        mAdapter.onRename(index, newTitle);
                    }
                });
    }

    private void deleteRecording(@NonNull RecordingData item) {
        mTaskExecutor.runTask(new DeleteRecordingTask(getContentResolver(), item.getUri()),
                () -> {
                    mAdapter.onDelete(item);
                    Utils.cancelShareNotification(this);
                });
    }

    private void deleteAllRecordings() {
        final List<Uri> uris = mAdapter.getData().stream()
                .map(RecordingData::getUri)
                .collect(Collectors.toList());
        mTaskExecutor.runTask(new DeleteAllRecordingsTask(getContentResolver(), uris), () -> {
            mAdapter.setData(Collections.emptyList());
            changeEmptyView(true);
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
        startActivity(RecordIntentHelper.getShareIntents(uris, TYPE_AUDIO));
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
                    selectedItems.forEach(this::deleteRecording);
                    Utils.cancelShareNotification(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptDeleteAllRecordings() {
        if (mAdapter.getItemCount() == 0) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_all_title)
                .setMessage(getString(R.string.delete_all_message))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteAllRecordings();
                    Utils.cancelShareNotification(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
