/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.list

interface RecordingListCallbacks : RecordingItemCallbacks {
    fun startSelectionMode()
    fun endSelectionMode()
}
