/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.lineageos.recorder.ext.scheduleShowSoftInput
import org.lineageos.recorder.list.ListActionModeCallback
import org.lineageos.recorder.list.RecordingData
import org.lineageos.recorder.list.RecordingListCallbacks
import org.lineageos.recorder.list.RecordingsAdapter
import org.lineageos.recorder.task.DeleteAllRecordingsTask
import org.lineageos.recorder.task.DeleteRecordingTask
import org.lineageos.recorder.task.GetRecordingsTask
import org.lineageos.recorder.task.RenameRecordingTask
import org.lineageos.recorder.task.TaskExecutor
import org.lineageos.recorder.utils.RecordIntentHelper
import org.lineageos.recorder.utils.Utils
import java.util.function.Consumer
import java.util.stream.Collectors

class ListActivity : AppCompatActivity(), RecordingListCallbacks {
    // Views
    private val contentView by lazy { findViewById<View>(android.R.id.content) }
    private val listEmptyTextView by lazy { findViewById<TextView>(R.id.listEmptyTextView) }
    private val listLoadingProgressBar by lazy { findViewById<ProgressBar>(R.id.listLoadingProgressBar) }
    private val listRecyclerView by lazy { findViewById<RecyclerView>(R.id.listRecyclerView) }
    private val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }

    // System services
    private val inputMethodManager by lazy { getSystemService(InputMethodManager::class.java) }

    // Adapters
    private val adapter by lazy {
        RecordingsAdapter(this)
    }

    private var actionMode: ActionMode? = null

    private val taskExecutor = TaskExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(taskExecutor)

        setContentView(R.layout.activity_list)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setSupportActionBar(toolbar)
        supportActionBar?.let {
            it.setDisplayShowHomeEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)

                if (adapter.itemCount == 0) {
                    changeEmptyView(true)
                    endSelectionMode()
                }
            }
        })

        listRecyclerView.layoutManager = LinearLayoutManager(this)
        listRecyclerView.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            listRecyclerView.updatePadding(
                bottom = insets.bottom,
                left = insets.left,
                right = insets.right,
            )

            windowInsets
        }

        loadRecordings()
    }

    override fun onPlay(uri: Uri) {
        startActivity(RecordIntentHelper.getOpenIntent(uri, TYPE_AUDIO))
    }

    override fun onShare(uri: Uri) {
        startActivity(RecordIntentHelper.getShareIntent(uri, TYPE_AUDIO))
    }

    override fun onDelete(index: Int, uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_recording_message)
            .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                taskExecutor.runTask(
                    DeleteRecordingTask(contentResolver, uri)
                ) {
                    adapter.onDelete(index)
                    Utils.cancelShareNotification(this)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onRename(index: Int, uri: Uri, currentName: String) {
        lateinit var alertDialog: AlertDialog
        lateinit var editText: EditText

        val onConfirm = {
            editText.text?.takeIf { it.isNotEmpty() }?.let { editable ->
                val newTitle = editable.toString()
                if (newTitle != currentName) {
                    renameRecording(uri, newTitle, index)
                }

                true
            } ?: false
        }

        val view = layoutInflater.inflate(
            R.layout.dialog_content_rename,
            null,
            false
        )
        editText = view.findViewById<EditText>(R.id.nameEditText).apply {
            setText(currentName)
            setSelection(0, currentName.length)
            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_UNSPECIFIED,
                    EditorInfo.IME_ACTION_DONE -> {
                        onConfirm().also {
                            if (it) {
                                alertDialog.dismiss()
                            }
                        }
                    }

                    else -> false
                }
            }
        }

        alertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.list_edit_title)
            .setView(view)
            .setPositiveButton(R.string.list_edit_confirm) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also {
                editText.requestFocus()
                inputMethodManager.scheduleShowSoftInput(editText, 0)
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_list, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val deleteAllItem = menu.findItem(R.id.action_delete_all)
        val hasItems = adapter.itemCount > 0
        deleteAllItem.setEnabled(hasItems)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_delete_all -> {
            promptDeleteAllRecordings()
            true
        }

        else -> false
    }

    override fun startSelectionMode() {
        // Clear previous (should do nothing), but be sure
        endSelectionMode()
        // Start action mode
        actionMode = toolbar.startActionMode(
            ListActionModeCallback({
                deleteSelectedRecordings()
            }) { shareSelectedRecordings() }
        )
        adapter.enterSelectionMode()
    }

    override fun endSelectionMode() {
        actionMode?.finish()
        actionMode = null

        adapter.exitSelectionMode()
    }

    override fun onActionModeFinished(mode: ActionMode) {
        super.onActionModeFinished(mode)
        endSelectionMode()
    }

    private fun loadRecordings() {
        taskExecutor.runTask(
            GetRecordingsTask(
                applicationContext.packageName,
                contentResolver
            )
        ) { list: List<RecordingData> ->
            listLoadingProgressBar.isVisible = false
            adapter.data = list
            changeEmptyView(list.isEmpty())
        }
    }

    private fun renameRecording(uri: Uri, newTitle: String, index: Int) {
        taskExecutor.runTask(
            RenameRecordingTask(contentResolver, uri, newTitle)
        ) { success: Boolean ->
            if (success) {
                adapter.onRename(index, newTitle)
            }
        }
    }

    private fun deleteRecording(item: RecordingData) {
        taskExecutor.runTask(
            DeleteRecordingTask(contentResolver, item.uri)
        ) {
            adapter.onDelete(item)
            Utils.cancelShareNotification(this)
        }
    }

    private fun deleteAllRecordings() {
        val uris = adapter.data.stream()
            .map { obj: RecordingData -> obj.uri }
            .collect(Collectors.toList())
        taskExecutor.runTask(DeleteAllRecordingsTask(contentResolver, uris)) {
            adapter.data = emptyList()
            changeEmptyView(true)
        }
    }

    private fun changeEmptyView(isEmpty: Boolean) {
        listEmptyTextView.isVisible = isEmpty
        listRecyclerView.isVisible = !isEmpty
    }

    private fun shareSelectedRecordings() {
        val selectedItems = adapter.selected

        if (selectedItems.isEmpty()) {
            return
        }

        val uris = selectedItems.stream()
            .map { obj: RecordingData -> obj.uri }
            .collect(
                Collectors.toCollection { mutableListOf() }
            )

        startActivity(RecordIntentHelper.getShareIntents(uris, TYPE_AUDIO))
    }

    private fun deleteSelectedRecordings() {
        val selectedItems = adapter.selected

        if (selectedItems.isEmpty()) {
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_selected_title)
            .setMessage(getString(R.string.delete_selected_message))
            .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                selectedItems.forEach(Consumer { item: RecordingData -> deleteRecording(item) })
                Utils.cancelShareNotification(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptDeleteAllRecordings() {
        if (adapter.itemCount == 0) {
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_all_title)
            .setMessage(getString(R.string.delete_all_message))
            .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                deleteAllRecordings()
                Utils.cancelShareNotification(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val TYPE_AUDIO = "audio/*"
    }
}
