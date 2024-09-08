/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder

import android.content.DialogInterface
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.recorder.ext.getCurrentSelection
import org.lineageos.recorder.list.ListActionModeCallback
import org.lineageos.recorder.list.RecordingItemCallbacks
import org.lineageos.recorder.models.Recording
import org.lineageos.recorder.list.RecordingsAdapter
import org.lineageos.recorder.utils.RecordIntentHelper
import org.lineageos.recorder.viewmodels.RecordingsViewModel
import kotlin.reflect.cast

class ListActivity : AppCompatActivity() {
    // View models
    private val model: RecordingsViewModel by viewModels()

    // Views
    private val contentView by lazy { findViewById<View>(android.R.id.content) }
    private val listEmptyTextView by lazy { findViewById<TextView>(R.id.listEmptyTextView) }
    private val listLoadingProgressBar by lazy { findViewById<ProgressBar>(R.id.listLoadingProgressBar) }
    private val listRecyclerView by lazy { findViewById<RecyclerView>(R.id.listRecyclerView) }
    private val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }

    // Adapters
    private val recordingItemCallbacks = object : RecordingItemCallbacks {
        override fun onPlay(recording: Recording) {
            this@ListActivity.onPlay(recording)
        }

        override fun onShare(recording: Recording) {
            this@ListActivity.onShare(recording)
        }

        override fun onDelete(recording: Recording) {
            this@ListActivity.onDelete(recording)
        }

        override fun onRename(recording: Recording) {
            this@ListActivity.onRename(recording)
        }
    }
    private val recordingsAdapter by lazy { RecordingsAdapter(recordingItemCallbacks) }

    // Selection
    private var selectionTracker: SelectionTracker<Recording>? = null

    private val selectionTrackerObserver =
        object : SelectionTracker.SelectionObserver<Recording>() {
            override fun onSelectionChanged() {
                super.onSelectionChanged()

                updateSelection()
            }

            override fun onSelectionRefresh() {
                super.onSelectionRefresh()

                updateSelection()
            }

            override fun onSelectionRestored() {
                super.onSelectionRestored()

                updateSelection()
            }
        }

    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            requireActivity().menuInflater.inflate(
                when (bucketId) {
                    MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> R.menu.album_action_bar_trash
                    else -> R.menu.album_action_bar
                },
                menu
            )
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) =
            selectionTracker?.selection?.toList()?.toTypedArray()?.takeUnless {
                it.isEmpty()
            }?.let { selection ->
                when (item?.itemId) {
                    R.id.deleteForever -> {
                        MediaDialogsUtils.openDeleteForeverDialog(requireContext(), *selection) {
                            deleteForeverContract.launch(
                                requireContext().contentResolver.createDeleteRequest(
                                    *it.map { media ->
                                        media.uri
                                    }.toTypedArray()
                                )
                            )
                        }

                        true
                    }

                    R.id.share -> {
                        requireActivity().startActivity(buildShareIntent(*selection))

                        true
                    }

                    else -> false
                }
            } ?: false

        override fun onDestroyActionMode(mode: ActionMode?) {
            selectionTracker?.clearSelection()
        }
    }

    private val inSelectionModeObserver = Observer { inSelectionMode: Boolean ->
        if (inSelectionMode) {
            startSelectionMode()
        } else {
            endSelectionMode()
        }
    }

    private var lastProcessedSelection: Array<out Recording>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_list)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setSupportActionBar(toolbar)
        supportActionBar?.let {
            it.setDisplayShowHomeEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        listRecyclerView.layoutManager = LinearLayoutManager(this)
        listRecyclerView.adapter = recordingsAdapter

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            listRecyclerView.updatePadding(
                bottom = insets.bottom,
                left = insets.left,
                right = insets.right,
            )

            windowInsets
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.recordings.collectLatest {
                    recordingsAdapter.submitList(it)

                    listLoadingProgressBar.isVisible = false

                    val isEmpty = it.isEmpty()
                    changeEmptyView(isEmpty)
                    if (isEmpty) {
                        endSelectionMode()
                    }
                }
            }
        }
    }

    fun onPlay(recording: Recording) {
        startActivity(RecordIntentHelper.getOpenIntent(recording.uri, TYPE_AUDIO))
    }

    fun onShare(recording: Recording) {
        startActivity(RecordIntentHelper.getShareIntent(recording.uri, TYPE_AUDIO))
    }

    fun onDelete(recording: Recording) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_recording_message)
            .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    model.deleteRecordings(recording)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun onRename(recording: Recording) {
        lateinit var alertDialog: AlertDialog
        lateinit var editText: EditText

        val onConfirm = {
            editText.text?.takeIf { it.isNotEmpty() }?.let { editable ->
                lifecycleScope.launch {
                    model.renameRecording(recording, editable.toString())
                }

                true
            } ?: false
        }

        val view = FrameLayout::class.cast(
            layoutInflater.inflate(
                R.layout.dialog_content_rename,
                null,
                false
            )
        )
        editText = view.findViewById<EditText>(R.id.nameEditText).apply {
            setText(recording.title)
            setSelection(0, recording.title.length)
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
        val hasItems = recordingsAdapter.itemCount > 0
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

    fun startSelectionMode() {
        // Clear previous (should do nothing), but be sure
        endSelectionMode()
        // Start action mode
        actionMode = toolbar.startActionMode(
            ListActionModeCallback({
                deleteSelectedRecordings()
            }) { shareSelectedRecordings() }
        )
        // TODO
        //adapter.enterSelectionMode()
    }

    fun endSelectionMode() {
        actionMode?.finish()
        actionMode = null

        // TODO
        //adapter.exitSelectionMode()
    }

    override fun onActionModeFinished(mode: ActionMode) {
        super.onActionModeFinished(mode)
        endSelectionMode()
    }

    private fun changeEmptyView(isEmpty: Boolean) {
        listEmptyTextView.isVisible = isEmpty
        listRecyclerView.isVisible = !isEmpty
    }

    private fun shareSelectedRecordings() {
        selectionTracker?.getCurrentSelection()
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?.let { selectedItems ->
                val uris = selectedItems
                    .map { it.uri }

                startActivity(RecordIntentHelper.getShareIntents(uris, TYPE_AUDIO))
            }
    }

    private fun deleteSelectedRecordings() {
        selectionTracker?.getCurrentSelection()
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?.let { selectedItems ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_selected_title)
                    .setMessage(getString(R.string.delete_selected_message))
                    .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                        lifecycleScope.launch {
                            model.deleteRecordings(*selectedItems.toTypedArray())
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
    }

    private fun promptDeleteAllRecordings() {
        if (recordingsAdapter.itemCount == 0) {
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_all_title)
            .setMessage(getString(R.string.delete_all_message))
            .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    model.deleteRecordings(*recordingsAdapter.currentList.toTypedArray())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val TYPE_AUDIO = "audio/*"
    }
}
