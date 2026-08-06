// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CockpitDisplaySelectorTest {
    @Test
    fun selectsOnlyActivePresentationDisplay() {
        val candidates = listOf(
            candidate(displayId = 0, isDefault = true, isPresentation = true),
            candidate(displayId = 2, isPresentation = false),
            candidate(displayId = 4, isPresentation = true)
        )

        assertEquals(4, selectCockpitDisplayId(candidates))
    }

    @Test
    fun fallsBackWhenPresentationDisplayIsUnavailable() {
        assertNull(
            selectCockpitDisplayId(
                listOf(
                    candidate(displayId = 2, isPresentation = false),
                    candidate(displayId = 4, isPresentation = true, isOn = false)
                )
            )
        )
    }

    private fun candidate(
        displayId: Int,
        isDefault: Boolean = false,
        isPresentation: Boolean,
        isOn: Boolean = true,
        isValid: Boolean = true
    ) = CockpitDisplayCandidate(
        displayId = displayId,
        isDefault = isDefault,
        isPresentation = isPresentation,
        isOn = isOn,
        isValid = isValid
    )
}
