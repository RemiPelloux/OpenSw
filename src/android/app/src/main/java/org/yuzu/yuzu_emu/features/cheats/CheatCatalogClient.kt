// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cheats

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

data class CatalogCheat(
    val titleId: String,
    val buildId: String,
    val source: String,
    val author: String,
    val date: String,
    val sha256: String,
    val text: String,
    val fromOfflineCache: Boolean = false
)

class CheatCatalogClient(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val preferences = context.getSharedPreferences("opensw_cheat_catalog", Context.MODE_PRIVATE)
    private val cacheDir = File(context.cacheDir, "cheat-catalog").apply { mkdirs() }

    fun fetch(titleId: String, buildId: String, force: Boolean = false): List<CatalogCheat> {
        val normalizedTitle = CheatTextValidator.requireBuildId(titleId)
        val normalizedBuild = CheatTextValidator.requireBuildId(buildId)
        return listOfNotNull(
            fetchHamlet(normalizedTitle, normalizedBuild, force),
            fetchGraphics(normalizedTitle, normalizedBuild, force)
        )
    }

    private fun fetchHamlet(titleId: String, buildId: String, force: Boolean): CatalogCheat? {
        val source = "switch-cheats-db"
        val url = "https://raw.githubusercontent.com/HamletDuFromage/switch-cheats-db/" +
            "master/cheats/$titleId.json"
        val cached = fetchCached(source, titleId, buildId, url, force) ?: return null
        val parsed = parseHamlet(cached.body, buildId) ?: return null
        val text = parsed.text
        CheatTextValidator.validate(text)
        return cached.toCatalog(titleId, buildId, source, parsed.author, text)
    }

    private fun fetchGraphics(titleId: String, buildId: String, force: Boolean): CatalogCheat? {
        val source = "NX-60FPS-RES-GFX-Cheats"
        val url = "https://raw.githubusercontent.com/ChanseyIsTheBest/" +
            "NX-60FPS-RES-GFX-Cheats/main/titles/$titleId/cheats/$buildId.txt"
        val cached = fetchCached(source, titleId, buildId, url, force) ?: return null
        val text = cached.body.trim()
        if (text.isEmpty()) return null
        CheatTextValidator.validate(text)
        return cached.toCatalog(titleId, buildId, source, "ChanseyIsTheBest contributors", text)
    }

    private fun fetchCached(
        source: String,
        titleId: String,
        buildId: String,
        url: String,
        force: Boolean
    ): CachedResponse? {
        val key = "$source-$titleId-$buildId"
        val cache = File(cacheDir, "$key.cache")
        val age = System.currentTimeMillis() - preferences.getLong("$key.time", 0L)
        if (!force && cache.exists() && age < CACHE_TTL_MS) {
            return CachedResponse(
                cache.readText(),
                preferences.getString("$key.date", "") ?: "",
                false
            )
        }

        val request = Request.Builder().url(url).header("User-Agent", "OpenSw")
            .apply {
                preferences.getString("$key.etag", null)?.let { header("If-None-Match", it) }
            }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 304 && cache.exists()) {
                    preferences.edit().putLong("$key.time", System.currentTimeMillis()).apply()
                    return CachedResponse(
                        cache.readText(),
                        preferences.getString("$key.date", "") ?: "",
                        false
                    )
                }
                if (response.code == 404) return null
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $source")
                val body = response.body?.bytes() ?: throw IOException("Empty response from $source")
                require(body.size <= MAX_RESPONSE_BYTES) { "Catalog response is too large" }
                cache.writeBytes(body)
                val date = response.header("Last-Modified") ?: Instant.now().toString()
                preferences.edit()
                    .putLong("$key.time", System.currentTimeMillis())
                    .putString("$key.etag", response.header("ETag"))
                    .putString("$key.date", date)
                    .apply()
                return CachedResponse(body.toString(Charsets.UTF_8), date, false)
            }
        } catch (error: Exception) {
            if (cache.exists()) {
                return CachedResponse(
                    cache.readText(),
                    preferences.getString("$key.date", "") ?: "",
                    true
                )
            }
            throw error
        }
    }

    private fun CachedResponse.toCatalog(
        titleId: String,
        buildId: String,
        source: String,
        author: String,
        text: String
    ) = CatalogCheat(
        titleId,
        buildId,
        source,
        author,
        date,
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHex(),
        text,
        offline
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private data class CachedResponse(val body: String, val date: String, val offline: Boolean)

    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

        internal fun parseHamlet(body: String, buildId: String): ParsedHamlet? {
            val root = jacksonObjectMapper().readTree(body)
            val build = root.get(buildId) ?: return null
            val text = build.fields().asSequence()
                .filter { it.value.isTextual }
                .joinToString("\n") { it.value.asText().trim() }
                .trim()
            if (text.isEmpty()) return null
            val author = root.path("attribution").fields().asSequence()
                .joinToString(" · ") { it.value.asText().trim() }
                .ifEmpty { "HamletDuFromage contributors" }
            return ParsedHamlet(text, author)
        }
    }
}

internal data class ParsedHamlet(val text: String, val author: String)
