/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import org.lineageos.recorder.R

class ListActionModeCallback(
    private val deleteSelected: Runnable,
    private val shareSelected: Runnable
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.menu_list_action_mode, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = when(item.itemId) {
        R.id.action_delete_selected -> {
            deleteSelected.run()
            true
        }
        R.id.action_share_selected -> {
            shareSelected.run()
            true
        }
        else -> false
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
