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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.recorder.R;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingItemViewHolder> {
    // According to documentation, DiffUtil is not working with lists of size > 2^26
    private static final int DIFF_MAX_SIZE = 1 << 26;

    @NonNull
    private final RecordingListCallbacks mCallbacks;
    @NonNull
    private final DateTimeFormatter mDateFormat = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm", Locale.getDefault());
    @NonNull
    private List<RecordingData> mData;
    @NonNull
    private List<RecordingData> mSelected;
    private boolean mInSelectionMode;

    public RecordingsAdapter(@NonNull RecordingListCallbacks callbacks) {
        mCallbacks = callbacks;
        mData = new ArrayList<>();
        mSelected = new ArrayList<>();
        mInSelectionMode = false;
    }

    @NonNull
    @Override
    public RecordingItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);
        final RecordingItemViewHolder viewHolder = new RecordingItemViewHolder(view,
                mCallbacks, mDateFormat);
        viewHolder.itemView.setOnClickListener(v -> {
            if (mInSelectionMode) {
                changeSelectedState(viewHolder.getAdapterPosition());
            } else {
                mCallbacks.onPlay(viewHolder.getUri());
            }
        });
        viewHolder.itemView.setOnLongClickListener(v -> {
            changeSelectedState(viewHolder.getAdapterPosition());
            return true;
        });
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingItemViewHolder holder, int position) {
        final RecordingData item = mData.get(position);
        final int selectionStatus = mSelected.isEmpty()
                ? ListItemStatus.DEFAULT
                : mSelected.contains(item) ? ListItemStatus.CHECKED : ListItemStatus.UNCHECKED;
        holder.setData(item, selectionStatus);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void setData(@NonNull List<RecordingData> data) {
        mSelected = new ArrayList<>(data.size());
        mInSelectionMode = false;

        if (data.size() < DIFF_MAX_SIZE) {
            final DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(mData, data));
            mData = data;
            diff.dispatchUpdatesTo(this);
        } else {
            mData = data;
            notifyItemRangeChanged(0, data.size());
        }
    }

    @NonNull
    public List<RecordingData> getData() {
        return mData;
    }

    public void onDelete(int index) {
        mSelected.remove(mData.remove(index));
        notifyItemRemoved(index);
    }

    public void onDelete(@NonNull RecordingData item) {
        final int index = mData.indexOf(item);
        if (index >= 0) {
            onDelete(index);
        }
    }

    public void onRename(int index, @NonNull String newTitle) {
        RecordingData oldData = mData.get(index);
        RecordingData newData = new RecordingData(oldData.getUri(), newTitle, oldData.getDateTime(),
                oldData.getDuration());
        mData.set(index, newData);

        final int selectIndex = mSelected.indexOf(oldData);
        if (selectIndex >= 0) {
            mSelected.set(selectIndex, newData);
        }
        notifyItemChanged(index);
    }

    @NonNull
    public List<RecordingData> getSelected() {
        return new ArrayList<>(mSelected);
    }

    private void changeSelectedState(int position) {
        if (!mInSelectionMode) {
            mCallbacks.startSelectionMode();
        }

        final RecordingData item = mData.get(position);
        if (mSelected.contains(item)) {
            mSelected.remove(item);
        } else {
            mSelected.add(mData.get(position));
        }
        notifyItemChanged(position);

        if (mSelected.isEmpty()) {
            mCallbacks.endSelectionMode();
        }
    }

    public void enterSelectionMode() {
        mInSelectionMode = true;
        notifyItemRangeChanged(0, mData.size());
    }

    public void exitSelectionMode() {
        mSelected.clear();
        mInSelectionMode = false;
        notifyItemRangeChanged(0, mData.size());
    }

    private static class DiffCallback extends DiffUtil.Callback {
        @NonNull
        private final List<RecordingData> oldList;
        @NonNull
        private final List<RecordingData> newList;

        public DiffCallback(@NonNull List<RecordingData> oldList,
                            @NonNull List<RecordingData> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getUri()
                    .equals(newList.get(newItemPosition).getUri());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }
    }
}
