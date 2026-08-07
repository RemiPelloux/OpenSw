// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSwSessionSnapshotTest {
    @Test
    fun parsesVersionedNativeSnapshot() {
        val snapshot = OpenSwSessionSnapshot.from(longArrayOf(1, 7, 2, 0x1234, 1))

        assertEquals(7L, snapshot?.generation)
        assertEquals(OpenSwSessionState.RUNNING, snapshot?.state)
        assertEquals(0x1234L, snapshot?.titleId)
        assertTrue(snapshot?.surfaceAttached == true)
    }

    @Test
    fun rejectsWrongSchemaAndState() {
        assertNull(OpenSwSessionSnapshot.from(longArrayOf(2, 7, 2, 0x1234, 1)))
        assertNull(OpenSwSessionSnapshot.from(longArrayOf(1, 7, 99, 0x1234, 1)))
    }
}
