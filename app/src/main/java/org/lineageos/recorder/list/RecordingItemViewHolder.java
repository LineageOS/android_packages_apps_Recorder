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

import android.annotation.SuppressLint;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.recorder.R;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class RecordingItemViewHolder extends RecyclerView.ViewHolder {
    private static final String SUMMARY_FORMAT = "%s - %02d:%02d";

    private final SimpleDateFormat mDateFormat;
    private final ImageView mIconView;
    private final TextView mTitleView;
    private final TextView mSummaryView;
    @NonNull
    private final RecordingItemCallbacks mCallbacks;
    private Uri mUri;

    public RecordingItemViewHolder(@NonNull View itemView,
                                   @NonNull RecordingItemCallbacks callbacks,
                                   @NonNull SimpleDateFormat dateFormat) {
        super(itemView);

        mCallbacks = callbacks;

        mDateFormat = dateFormat;
        mIconView = itemView.findViewById(R.id.item_play);
        mTitleView = itemView.findViewById(R.id.item_title);
        mSummaryView = itemView.findViewById(R.id.item_date);
        final ImageView menuView = itemView.findViewById(R.id.item_menu);
        menuView.setOnClickListener(this::showPopupMenu);
    }

    public void setData(@NonNull RecordingData data,
                        @ListItemStatus int selection) {
        mUri = data.getUri();
        mTitleView.setText(data.getTitle());
        long seconds = data.getDuration() / 1000;
        long minutes = seconds / 60;
        seconds -= (minutes * 60);
        mSummaryView.setText(String.format(Locale.getDefault(), SUMMARY_FORMAT,
                mDateFormat.format(data.getDate()), minutes, seconds));

        switch (selection) {
            case ListItemStatus.DEFAULT:
                mIconView.setImageResource(R.drawable.ic_play_circle_outline);
                break;
            case ListItemStatus.UNCHECKED:
                mIconView.setImageResource(R.drawable.ic_list_unchecked);
                break;
            case ListItemStatus.CHECKED:
                mIconView.setImageResource(R.drawable.ic_list_checked);
                break;
        }
    }

    public Uri getUri() {
        return mUri;
    }

    @SuppressLint("RestrictedApi")
    private void showPopupMenu(@NonNull View view) {
        final ContextThemeWrapper wrapper = new ContextThemeWrapper(
                itemView.getContext(),
                R.style.AppTheme_Main_PopupMenuOverlapAnchor
        );
        final PopupMenu popupMenu = new PopupMenu(wrapper, view, Gravity.NO_GRAVITY,
                R.attr.actionOverflowButtonStyle, 0);
        popupMenu.inflate(R.menu.menu_list_item);
        popupMenu.setOnMenuItemClickListener(item -> onActionSelected(item.getItemId()));
        final MenuPopupHelper helper = new MenuPopupHelper(
                wrapper,
                (MenuBuilder) popupMenu.getMenu(),
                view
        );
        helper.setForceShowIcon(true);
        helper.show();
    }

    private boolean onActionSelected(int actionId) {
        final int index = getAdapterPosition();
        if (actionId == R.id.action_rename) {
            mCallbacks.onRename(index, mUri, mTitleView.getText().toString());
        } else if (actionId == R.id.action_share) {
            mCallbacks.onShare(mUri);
        } else if (actionId == R.id.action_delete) {
            mCallbacks.onDelete(index, mUri);
        } else {
            return false;
        }
        return true;
    }
}
