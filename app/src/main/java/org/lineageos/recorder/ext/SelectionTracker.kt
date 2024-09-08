/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.ext

import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.SelectionTracker

/**
 * Get the current election.
 * @see SelectionTracker.getSelection
 * @see SelectionTracker.copySelection
 */
fun <T> SelectionTracker<T>.getCurrentSelection() = MutableSelection<T>().apply {
    copySelection(this)
}
