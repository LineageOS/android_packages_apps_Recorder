/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.flow

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.os.bundleOf
import kotlinx.coroutines.flow.Flow
import org.lineageos.recorder.ext.mapEachRow
import org.lineageos.recorder.ext.queryFlow
import org.lineageos.recorder.models.Recording
import org.lineageos.recorder.query.Query
import org.lineageos.recorder.query.eq

class RecordingsFlow(
    val context: Context,
    val uri: Uri,
) : QueryFlow<Recording>() {
    override fun flowCursor(): Flow<Cursor?> {
        val selection = MediaStore.Audio.Media.OWNER_PACKAGE_NAME eq Query.ARG
        val selectionArgs = arrayOf(
            context.packageName,
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        return context.contentResolver.queryFlow(
            uri,
            projection,
            bundleOf(
                ContentResolver.QUERY_ARG_SQL_SELECTION to selection.build(),
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to selectionArgs,
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
            ),
        )
    }

    override fun flowData() = flowCursor().mapEachRow(projection) { it, indexCache ->
        var i = 0

        val id = it.getLong(indexCache[i++])
        val displayName = it.getString(indexCache[i++])
        val dateAdded = it.getLong(indexCache[i++])
        val duration = it.getLong(indexCache[i++])

        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
        )

        Recording.fromMediaStore(
            uri,
            displayName,
            dateAdded,
            duration,
        )
    }

    companion object {
        private val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION
        )
    }
}
