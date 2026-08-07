// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderRuntimeSnapshotTest {
    @Test
    fun parsesVersionedNativeSnapshot() {
        val snapshot = RenderRuntimeSnapshot.from(
            longArrayOf(1, 0x01001F5010DFA000, 8, 7, 2, 1, 1, 0, 1, 60)
        )!!

        assertEquals(8, snapshot.requestedWorkers)
        assertEquals(7, snapshot.effectiveWorkers)
        assertEquals(PipelineWorkerReason.HARDWARE_CAPPED, snapshot.workerReason)
        assertEquals(true, snapshot.asyncShaders)
        assertEquals(false, snapshot.asyncPresentation)
    }

    @Test
    fun rejectsUnknownOrTruncatedSnapshots() {
        assertNull(RenderRuntimeSnapshot.from(longArrayOf()))
        assertNull(RenderRuntimeSnapshot.from(longArrayOf(2, 0, 4, 4, 1, 1, 1, 1, 1, 0)))
        assertNull(RenderRuntimeSnapshot.from(longArrayOf(1, 0, 4, 4, 99, 1, 1, 1, 1, 0)))
    }
}
