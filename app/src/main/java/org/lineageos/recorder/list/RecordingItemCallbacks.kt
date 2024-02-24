/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import android.net.Uri

interface RecordingItemCallbacks {
    fun onPlay(uri: Uri)
    fun onShare(uri: Uri)
    fun onDelete(index: Int, uri: Uri)
    fun onRename(index: Int, uri: Uri, currentName: String)
}
