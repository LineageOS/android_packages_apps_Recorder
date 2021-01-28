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

import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.recorder.R;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class RecordingItemViewHolder extends RecyclerView.ViewHolder {
    private static final String SUMMARY_FORMAT = "%s - %02d:%02d";

    private final SimpleDateFormat mDateFormat;
    private final TextView mTitleView;
    private final TextView mSummaryView;
    private Uri mUri;

    public RecordingItemViewHolder(@NonNull View itemView,
                                   @NonNull RecordingItemCallbacks callbacks,
                                   @NonNull SimpleDateFormat dateFormat) {
        super(itemView);

        mDateFormat = dateFormat;
        mTitleView = itemView.findViewById(R.id.item_title);
        mSummaryView = itemView.findViewById(R.id.item_date);
        ImageView playView = itemView.findViewById(R.id.item_play);
        ImageView shareView = itemView.findViewById(R.id.item_share);
        ImageView deleteView = itemView.findViewById(R.id.item_delete);

        itemView.setOnClickListener(v -> callbacks.onPlay(mUri));
        playView.setOnClickListener(v -> callbacks.onPlay(mUri));
        shareView.setOnClickListener(v -> callbacks.onShare(mUri));
        deleteView.setOnClickListener(v -> callbacks.onDelete(getAdapterPosition(), mUri));
    }

    public void setData(@NonNull RecordingData data) {
        mUri = data.getUri();
        mTitleView.setText(data.getTitle());
        long seconds = data.getDuration() / 1000;
        long minutes = seconds / 60;
        seconds -= (minutes * 60);
        mSummaryView.setText(String.format(Locale.getDefault(), SUMMARY_FORMAT,
                mDateFormat.format(data.getDate()), minutes, seconds));
    }
}
