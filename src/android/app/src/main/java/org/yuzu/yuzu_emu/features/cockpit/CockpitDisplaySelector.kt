// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cockpit

internal data class CockpitDisplayCandidate(
    val displayId: Int,
    val isDefault: Boolean,
    val isPresentation: Boolean,
    val isOn: Boolean,
    val isValid: Boolean
)

internal fun selectCockpitDisplayId(candidates: Collection<CockpitDisplayCandidate>): Int? =
    candidates.firstOrNull { candidate ->
        !candidate.isDefault &&
            candidate.isPresentation &&
            candidate.isOn &&
            candidate.isValid
    }?.displayId
