/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.recorder.R
import java.time.format.DateTimeFormatter
import java.util.Locale

class RecordingsAdapter(private val callbacks: RecordingListCallbacks) :
    RecyclerView.Adapter<RecordingItemViewHolder>() {
    private val dateFormat = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm", Locale.getDefault()
    )
    private var _data = mutableListOf<RecordingData>()
    private var _selected = mutableListOf<RecordingData>()
    private var inSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        val viewHolder = RecordingItemViewHolder(
            view,
            callbacks, dateFormat
        )
        viewHolder.itemView.setOnClickListener {
            if (inSelectionMode) {
                changeSelectedState(viewHolder.adapterPosition)
            } else {
                viewHolder.uri?.let {
                    callbacks.onPlay(it)
                }
            }
        }
        viewHolder.itemView.setOnLongClickListener {
            changeSelectedState(viewHolder.adapterPosition)
            true
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecordingItemViewHolder, position: Int) {
        val item = _data[position]
        val selectionStatus =
            if (_selected.isEmpty()) ListItemStatus.DEFAULT else if (_selected.contains(
                    item
                )
            ) ListItemStatus.CHECKED else ListItemStatus.UNCHECKED
        holder.setData(item, selectionStatus)
    }

    override fun getItemCount(): Int {
        return _data.size
    }

    var data: List<RecordingData>
        get() = _data
        set(data) {
            _selected = mutableListOf()
            inSelectionMode = false
            if (data.size < DIFF_MAX_SIZE) {
                val diff = DiffUtil.calculateDiff(DiffCallback(_data, data))
                _data = data.toMutableList()
                diff.dispatchUpdatesTo(this)
            } else {
                _data = data.toMutableList()
                notifyItemRangeChanged(0, data.size)
            }
        }

    fun onDelete(index: Int) {
        _selected.remove(_data.removeAt(index))
        notifyItemRemoved(index)
    }

    fun onDelete(item: RecordingData) {
        val index = _data.indexOf(item)
        if (index >= 0) {
            onDelete(index)
        }
    }

    fun onRename(index: Int, newTitle: String) {
        val oldData = _data[index]
        val newData = RecordingData(
            oldData.uri, newTitle, oldData.dateTime,
            oldData.duration
        )
        _data[index] = newData
        val selectIndex = _selected.indexOf(oldData)
        if (selectIndex >= 0) {
            _selected[selectIndex] = newData
        }
        notifyItemChanged(index)
    }

    val selected: List<RecordingData>
        get() = ArrayList(_selected)

    private fun changeSelectedState(position: Int) {
        if (!inSelectionMode) {
            callbacks.startSelectionMode()
        }
        val item = _data[position]
        if (_selected.contains(item)) {
            _selected.remove(item)
        } else {
            _selected.add(_data[position])
        }
        notifyItemChanged(position)
        if (_selected.isEmpty()) {
            callbacks.endSelectionMode()
        }
    }

    fun enterSelectionMode() {
        inSelectionMode = true
        notifyItemRangeChanged(0, _data.size)
    }

    fun exitSelectionMode() {
        _selected.clear()
        inSelectionMode = false
        notifyItemRangeChanged(0, _data.size)
    }

    private class DiffCallback(
        private val oldList: List<RecordingData>,
        private val newList: List<RecordingData>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return (oldList[oldItemPosition].uri
                    == newList[newItemPosition].uri)
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    companion object {
        // According to documentation, DiffUtil is not working with lists of size > 2^26
        private const val DIFF_MAX_SIZE = 1 shl 26
    }
}
