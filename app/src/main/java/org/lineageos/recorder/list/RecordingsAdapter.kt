/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.lineageos.recorder.R
import org.lineageos.recorder.models.Recording
import java.time.format.DateTimeFormatter
import java.util.Locale

class RecordingsAdapter(
    private val callbacks: RecordingItemCallbacks,
) : ListAdapter<Recording, RecordingItemViewHolder>(diffCallback) {
    private val dateFormat = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm", Locale.getDefault()
    )

    var selectionTracker: SelectionTracker<Recording>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        val viewHolder = RecordingItemViewHolder(
            view,
            callbacks,
            dateFormat
        )
        viewHolder.itemView.setOnClickListener {
            viewHolder.recording?.let {
                callbacks.onPlay(it)
            }
        }
        viewHolder.itemView.setOnLongClickListener {
            true
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecordingItemViewHolder, position: Int) {
        val item = getItem(position)

        val selectionStatus = selectionTracker?.takeIf { it.hasSelection() }?.let {
            when (it.isSelected(item)) {
                true -> ListItemStatus.CHECKED
                false -> ListItemStatus.UNCHECKED
            }
        } ?: ListItemStatus.DEFAULT

        holder.setData(item, selectionStatus)
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<Recording>() {
            override fun areItemsTheSame(
                oldItem: Recording,
                newItem: Recording
            ) = oldItem.uri == newItem.uri

            override fun areContentsTheSame(
                oldItem: Recording,
                newItem: Recording
            ) = oldItem == newItem
        }
    }
}
