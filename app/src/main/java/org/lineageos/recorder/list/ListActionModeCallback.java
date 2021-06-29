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

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.lineageos.recorder.R;

public class ListActionModeCallback implements ActionMode.Callback {

    @NonNull
    private final Runnable mDeleteSelected;
    @NonNull
    private final Runnable mShareSelected;

    public ListActionModeCallback(@NonNull Runnable deleteSelected,
                                  @NonNull Runnable shareSelected) {
        mDeleteSelected = deleteSelected;
        mShareSelected = shareSelected;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        final MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.menu_list_action_mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_delete_selected) {
            mDeleteSelected.run();
            return true;
        } else if (id == R.id.action_share_selected) {
            mShareSelected.run();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
    }
}
