// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

object AynThorDetector {
    fun matches(manufacturer: String, model: String, product: String): Boolean {
        val normalizedManufacturer = manufacturer.lowercase().filter(Char::isLetterOrDigit)
        val normalizedDevice = "$model$product".lowercase().filter(Char::isLetterOrDigit)
        return normalizedManufacturer.contains("ayn") && normalizedDevice.contains("thor")
    }
}
