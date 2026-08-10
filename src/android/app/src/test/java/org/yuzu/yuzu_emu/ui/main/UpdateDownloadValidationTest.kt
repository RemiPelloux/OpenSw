// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class UpdateDownloadValidationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `separate attempts never share a destination file`() {
        val first = createUpdateDownloadFile(temporaryFolder.root)
        val second = createUpdateDownloadFile(temporaryFolder.root)

        assertNotEquals(first.canonicalPath, second.canonicalPath)
    }

    @Test
    fun `accepts complete known and unknown length bodies`() {
        assertEquals(
            UpdateDownloadEvent.Succeeded,
            classifyUpdateDownload(true, true, 4L, 4L, false)
        )
        assertEquals(
            UpdateDownloadEvent.Succeeded,
            classifyUpdateDownload(true, true, -1L, 4L, false)
        )
    }

    @Test
    fun `rejects unsuccessful missing empty and partial responses`() {
        assertEquals(
            UpdateDownloadEvent.Failed,
            classifyUpdateDownload(false, true, 4L, 4L, false)
        )
        assertEquals(
            UpdateDownloadEvent.Failed,
            classifyUpdateDownload(true, false, -1L, 0L, false)
        )
        assertEquals(
            UpdateDownloadEvent.Failed,
            classifyUpdateDownload(true, true, 0L, 0L, false)
        )
        assertEquals(
            UpdateDownloadEvent.Failed,
            classifyUpdateDownload(true, true, 4L, 2L, false)
        )
    }

    @Test
    fun `cancellation wins over other terminal conditions`() {
        assertEquals(
            UpdateDownloadEvent.Cancelled,
            classifyUpdateDownload(true, true, 4L, 4L, true)
        )
    }
}
