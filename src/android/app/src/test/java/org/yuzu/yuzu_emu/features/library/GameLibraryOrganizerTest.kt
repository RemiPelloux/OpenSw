// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.library

import org.junit.Assert.assertEquals
import org.junit.Test

class GameLibraryOrganizerTest {
    @Test
    fun `favorites are first while preserving the default order`() {
        val result = GameLibraryOrganizer.organize(
            entries = listOf(
                entry("A"),
                entry("B", favorite = true),
                entry("C"),
                entry("D", favorite = true)
            ),
            sort = LibrarySort.DEFAULT,
            recentAfter = 0L
        )

        assertEquals(listOf("B", "D", "A", "C"), result)
    }

    @Test
    fun `recent sort keeps old favorites visible and first`() {
        val result = GameLibraryOrganizer.organize(
            entries = listOf(
                entry("Old favorite", favorite = true, played = 1L),
                entry("Recent", played = 200L),
                entry("Old", played = 2L)
            ),
            sort = LibrarySort.RECENTLY_PLAYED,
            recentAfter = 100L
        )

        assertEquals(listOf("Old favorite", "Recent"), result)
    }

    @Test
    fun `alphabetical ordering applies inside favorite groups`() {
        val result = GameLibraryOrganizer.organize(
            entries = listOf(
                entry("Zelda"),
                entry("Arceus", favorite = true),
                entry("Animal Crossing"),
                entry("Bayonetta", favorite = true)
            ),
            sort = LibrarySort.ALPHABETICAL,
            recentAfter = 0L
        )

        assertEquals(listOf("Arceus", "Bayonetta", "Animal Crossing", "Zelda"), result)
    }

    private fun entry(
        title: String,
        favorite: Boolean = false,
        played: Long = 0L
    ) = LibraryEntry(
        item = title,
        title = title,
        isFavorite = favorite,
        lastPlayedTime = played
    )
}
