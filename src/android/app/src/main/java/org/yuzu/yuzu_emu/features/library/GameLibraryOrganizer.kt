// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.library

import java.util.Locale

enum class LibrarySort {
    DEFAULT,
    ALPHABETICAL,
    RECENTLY_PLAYED,
    RECENTLY_ADDED
}

data class LibraryEntry<T>(
    val item: T,
    val title: String,
    val isFavorite: Boolean,
    val lastPlayedTime: Long = 0L,
    val addedTime: Long = 0L
)

object GameLibraryOrganizer {
    fun <T> organize(
        entries: List<LibraryEntry<T>>,
        sort: LibrarySort,
        recentAfter: Long
    ): List<T> {
        val eligible = when (sort) {
            LibrarySort.RECENTLY_PLAYED ->
                entries.filter { it.isFavorite || it.lastPlayedTime > recentAfter }
            LibrarySort.RECENTLY_ADDED ->
                entries.filter { it.isFavorite || it.addedTime > recentAfter }
            else -> entries
        }

        val comparator = compareByDescending<LibraryEntry<T>> { it.isFavorite }.let { favorites ->
            when (sort) {
                LibrarySort.ALPHABETICAL -> favorites
                    .thenBy { it.title.lowercase(Locale.ROOT) }
                LibrarySort.RECENTLY_PLAYED -> favorites
                    .thenByDescending { it.lastPlayedTime }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
                LibrarySort.RECENTLY_ADDED -> favorites
                    .thenByDescending { it.addedTime }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
                LibrarySort.DEFAULT -> favorites
            }
        }
        return eligible.sortedWith(comparator).map(LibraryEntry<T>::item)
    }
}
