/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import android.annotation.SuppressLint
import android.net.Uri
import android.text.format.DateUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.recorder.R
import java.time.format.DateTimeFormatter
import java.util.Locale

class RecordingItemViewHolder(
    itemView: View,
    private val callbacks: RecordingItemCallbacks,
    private val dateFormat: DateTimeFormatter
) : RecyclerView.ViewHolder(itemView) {
    // Views
    private val dateTextView by lazy { itemView.findViewById<TextView>(R.id.dateTextView) }
    private val menuImageView by lazy { itemView.findViewById<ImageView>(R.id.menuImageView) }
    private val playImageView by lazy { itemView.findViewById<ImageView>(R.id.playImageView) }
    private val titleTextView by lazy { itemView.findViewById<TextView>(R.id.titleTextView) }

    var uri: Uri? = null
        private set

    init {
        menuImageView.setOnClickListener { showPopupMenu(it) }
    }

    fun setData(data: RecordingData, selection: ListItemStatus) {
        uri = data.uri
        titleTextView.text = data.title
        val duration = data.duration / 1000
        dateTextView.text = String.format(
            Locale.getDefault(), SUMMARY_FORMAT,
            dateFormat.format(data.dateTime),
            DateUtils.formatElapsedTime(duration)
        )

        playImageView.setImageResource(
            when (selection) {
                ListItemStatus.DEFAULT -> R.drawable.ic_play_circle
                ListItemStatus.UNCHECKED -> R.drawable.ic_radio_button_unchecked
                ListItemStatus.CHECKED -> R.drawable.ic_check_circle
            }
        )
    }

    @SuppressLint("RestrictedApi")
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

    private fun onActionSelected(actionId: Int) = uri?.let {
        when (actionId) {
            R.id.action_rename -> {
                callbacks.onRename(adapterPosition, it, titleTextView.text.toString())
                true
            }

            R.id.action_share -> {
                callbacks.onShare(it)
                true
            }

            R.id.action_delete -> {
                callbacks.onDelete(adapterPosition, it)
                true
            }

            else -> false
        }
    } ?: false

    companion object {
        private const val SUMMARY_FORMAT = "%s - %s"
    }
}
