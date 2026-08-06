// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cheats

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yuzu.yuzu_emu.NativeLibrary

class CheatTextValidatorTest {
    @Test
    fun bundledForetalesCheatMatchesCurrentBuild() {
        val cheat = BundledCheats.foretales60Fps

        assertEquals("010026801939E000", CheatTextValidator.requireBuildId(cheat.titleId))
        assertEquals("F95A034961AF4385", CheatTextValidator.requireBuildId(cheat.buildId))
        assertTrue(CheatTextValidator.validate(cheat.text).hasMastercode)
        val digest = MessageDigest.getInstance("SHA-256").digest(cheat.text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(cheat.sha256, digest)
    }

    @Test
    fun validatesMastercodeAndRegularSections() {
        val result = CheatTextValidator.validate(
            """
            {Mastercode}
            04000000 00000000 00000001
            [Shiny Pokemon On]
            04000000 00000004 00000001
            """.trimIndent()
        )

        assertTrue(result.hasMastercode)
        assertEquals(6, result.opcodeCount)
        assertEquals(setOf("Mastercode", "Shiny Pokemon On"), result.sections.keys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanVmOpcodeLimit() {
        val opcodes = List(0x401) { "04000000" }.joinToString(" ")
        CheatTextValidator.validate("[Too large]\n$opcodes")
    }

    @Test
    fun reportsSectionConflicts() {
        val diff = CheatInstaller.diff(
            "[Keep]\n04000000\n[Remove]\n04000001",
            "[Keep]\n04000002\n[Add]\n04000003"
        )

        assertEquals(setOf("Add"), diff.added)
        assertEquals(setOf("Remove"), diff.removed)
        assertEquals(setOf("Keep"), diff.modified)
        assertFalse(diff.modified.contains("Add"))
    }

    @Test
    fun stateKeyChangesWithOpcodeFingerprint() {
        val context = NativeLibrary.CheatContext("01001F5010DFA000", "AEE8F150DDA1B5A8")
        val original = NativeLibrary.CheatEntry(1, "Shiny", false, false, "old-hash", "local")
        val updated = original.copy(fingerprint = "new-hash")

        assertNotEquals(CheatStateKey.of(context, original), CheatStateKey.of(context, updated))
    }

    @Test
    fun catalogRequiresExactBuildId() {
        val json =
            """{
                "AEE8F150DDA1B5A8": {"[60 FPS]": "[60 FPS]\\n04000000 00000000 00000001"},
                "attribution": {"Pokemon.txt": "By tester"}
            }
            """.trimIndent()

        val exact = CheatCatalogClient.parseHamlet(json, "AEE8F150DDA1B5A8")
        assertTrue(exact?.text?.contains("[60 FPS]") == true)
        assertEquals("By tester", exact?.author)
        assertNull(CheatCatalogClient.parseHamlet(json, "0000000000000000"))
    }

    @Test
    fun importTreeRejectsAnotherTitleId() {
        val currentTitle = "01001F5010DFA000"
        assertTrue(
            CheatImportPolicy.acceptsTreePath(
                "$currentTitle/cheats/AEE8F150DDA1B5A8.txt",
                currentTitle
            )
        )
        assertFalse(
            CheatImportPolicy.acceptsTreePath(
                "010051701FB46000/cheats/AEE8F150DDA1B5A8.txt",
                currentTitle
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsZipSlipPath() {
        CheatImportPolicy.safeZipPath("../01001F5010DFA000/cheats/code.txt")
    }
}
