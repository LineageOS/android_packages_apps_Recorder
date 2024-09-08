/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.recorder.R
import org.lineageos.recorder.models.Recording
import org.lineageos.recorder.viewmodels.RecordingsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

class RecordingsAdapter(
    private val model: RecordingsViewModel,
    private val callbacks: RecordingItemCallbacks,
) : ListAdapter<Recording, RecordingsAdapter.ViewHolder>(diffCallback) {
    // We store a reverse lookup list for performance reasons
    private var recordingToIndex: Map<Recording, Int>? = null

    var selectionTracker: SelectionTracker<Recording>? = null

    val itemKeyProvider = object : ItemKeyProvider<Recording>(SCOPE_CACHED) {
        override fun getKey(position: Int) = getItem(position)

        override fun getPosition(key: Recording) = recordingToIndex?.get(key) ?: -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = getItem(position)

        val selectionStatus = selectionTracker?.isSelected(recording) == true

        holder.bind(recording, selectionStatus)
    }

    override fun onCurrentListChanged(
        previousList: MutableList<Recording>,
        currentList: MutableList<Recording>
    ) {
        super.onCurrentListChanged(previousList, currentList)

        // This gets randomly called with null as argument
        if (currentList == null) {
            return
        }

        val dataTypeToIndex = mutableMapOf<Recording, Int>()
        for (i in currentList.indices) {
            dataTypeToIndex[currentList[i]] = i
        }
        this.recordingToIndex = dataTypeToIndex.toMap()
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)

        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)

        holder.onViewDetachedToWindow()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Views
        private val dateTextView by lazy { itemView.findViewById<TextView>(R.id.dateTextView) }
        private val menuImageView by lazy { itemView.findViewById<ImageView>(R.id.menuImageView) }
        private val playImageView by lazy { itemView.findViewById<ImageView>(R.id.playImageView) }
        private val titleTextView by lazy { itemView.findViewById<TextView>(R.id.titleTextView) }

        private lateinit var recording: Recording

        // Selection
        private var inSelectionMode = false
        private var isSelected = false

        private val inSelectionModeObserver = Observer { inSelectionMode: Boolean ->
            this.inSelectionMode = inSelectionMode

            updateSelection()
        }

        val itemDetails = object : ItemDetailsLookup.ItemDetails<Recording>() {
            override fun getPosition() = bindingAdapterPosition
            override fun getSelectionKey() = recording
        }

        init {
            itemView.setOnClickListener { callbacks.onPlay(recording) }
            menuImageView.setOnClickListener { showPopupMenu(it) }
        }

        fun onViewAttachedToWindow() {
            itemView.findViewTreeLifecycleOwner()?.let {
                model.inSelectionMode.observe(it, inSelectionModeObserver)
            }
        }

        fun onViewDetachedToWindow() {
            model.inSelectionMode.removeObserver(inSelectionModeObserver)
        }

        fun bind(recording: Recording, isSelected: Boolean = false) {
            this.recording = recording
            this.isSelected = isSelected

            titleTextView.text = recording.title
            dateTextView.text = String.format(
                Locale.getDefault(), SUMMARY_FORMAT,
                dateFormatter.format(recording.dateAdded),
                timeFormatter.format(recording.dateAdded)
            )

            updateSelection()
        }

        private fun updateSelection(
            inSelectionMode: Boolean = this.inSelectionMode,
            isSelected: Boolean = this.isSelected,
        ) {
            playImageView.setImageResource(
                when (inSelectionMode) {
                    false -> R.drawable.ic_play_circle
                    true -> when (isSelected) {
                        false -> R.drawable.ic_radio_button_unchecked
                        true -> R.drawable.ic_check_circle
                    }
                }
            )
        }

        @Suppress("RestrictedApi")
        private fun showPopupMenu(view: View) {
            val wrapper = ContextThemeWrapper(
                itemView.context,
                R.style.AppTheme_PopupMenuOverlapAnchor
            )
            val popupMenu = PopupMenu(wrapper, view, Gravity.NO_GRAVITY)
            popupMenu.inflate(R.menu.menu_list_item)
            popupMenu.setOnMenuItemClickListener { item: MenuItem -> onActionSelected(item.itemId) }
            val helper = MenuPopupHelper(
                wrapper,
                (popupMenu.menu as MenuBuilder),
                view
            )
            helper.setForceShowIcon(true)
            helper.show()
        }

        private fun onActionSelected(actionId: Int) = when (actionId) {
            R.id.action_rename -> {
                callbacks.onRename(recording)
                true
            }

            R.id.action_share -> {
                callbacks.onShare(recording)
                true
            }

            R.id.action_delete -> {
                callbacks.onDelete(recording)
                true
            }

            else -> false
        }
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

        private const val SUMMARY_FORMAT = "%s - %s"

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()
    }
}
