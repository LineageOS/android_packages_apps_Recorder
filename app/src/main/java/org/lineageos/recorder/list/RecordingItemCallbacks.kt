/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

import org.lineageos.recorder.models.Recording

interface RecordingItemCallbacks {
    fun onPlay(recording: Recording)
    fun onShare(recording: Recording)
    fun onDelete(recording: Recording)
    fun onRename(recording: Recording)
}
