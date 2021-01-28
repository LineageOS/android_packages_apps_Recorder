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
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.recorder.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingItemViewHolder> {

    @NonNull
    private final RecordingItemCallbacks mCallbacks;
    @NonNull
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm", Locale.getDefault());
    @NonNull
    private List<RecordingData> mData;

    public RecordingsAdapter(@NonNull RecordingItemCallbacks callbacks) {
        mCallbacks = callbacks;
        mData = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecordingItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);
        return new RecordingItemViewHolder(view, mCallbacks, mDateFormat);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingItemViewHolder holder, int position) {
        holder.setData(mData.get(position));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void setData(@NonNull List<RecordingData> data) {
        mData = data;
        notifyDataSetChanged();
    }

    public void onDelete(int index) {
        mData.remove(index);
        notifyItemRemoved(index);
    }
}
