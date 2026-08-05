// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cheats

data class CheatValidation(
    val sections: Map<String, String>,
    val opcodeCount: Int,
    val hasMastercode: Boolean
)

object CheatTextValidator {
    private val buildIdPattern = Regex("^[0-9A-Fa-f]{16}$")
    private val opcodePattern = Regex("^[0-9A-Fa-f]{8}$")

    fun requireBuildId(buildId: String): String {
        require(buildIdPattern.matches(buildId)) { "Build ID must contain exactly 16 hex digits" }
        return buildId.uppercase()
    }

    fun validate(text: String): CheatValidation {
        require(text.toByteArray().size <= MAX_FILE_BYTES) { "Cheat file exceeds 1 MiB" }
        val sections = linkedMapOf<String, StringBuilder>()
        var currentName: String? = null
        var totalOpcodes = 0
        var currentOpcodes = 0
        var hasMaster = false

        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            val isMaster = line.startsWith('{') && line.endsWith('}')
            val isCheat = line.startsWith('[') && line.endsWith(']')
            if (isMaster || isCheat) {
                require(currentOpcodes <= MAX_SECTION_OPCODES) {
                    "Section exceeds 0x100 opcodes"
                }
                val name = line.substring(1, line.length - 1).trim()
                require(name.isNotEmpty()) { "Empty section name at line ${index + 1}" }
                currentName = name
                currentOpcodes = 0
                hasMaster = hasMaster || isMaster
                sections.getOrPut(name) { StringBuilder() }.appendLine(line)
                return@forEachIndexed
            }

            val sectionName = currentName
                ?: throw IllegalArgumentException("Opcode before a section at line ${index + 1}")
            val opcodes = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            require(opcodes.isNotEmpty() && opcodes.all(opcodePattern::matches)) {
                "Invalid opcode at line ${index + 1}"
            }
            currentOpcodes += opcodes.size
            totalOpcodes += opcodes.size
            require(currentOpcodes <= MAX_SECTION_OPCODES) { "Section exceeds 0x100 opcodes" }
            require(totalOpcodes <= MAX_PROGRAM_OPCODES) { "File exceeds 0x400 opcodes" }
            sections.getValue(sectionName).appendLine(opcodes.joinToString(" "))
        }
        require(sections.isNotEmpty() && totalOpcodes > 0) { "No valid cheat section" }
        return CheatValidation(
            sections.mapValues { it.value.toString().trimEnd() },
            totalOpcodes,
            hasMaster
        )
    }

    const val MAX_FILE_BYTES = 1024 * 1024
    const val MAX_SECTION_OPCODES = 0x100
    const val MAX_PROGRAM_OPCODES = 0x400
}
