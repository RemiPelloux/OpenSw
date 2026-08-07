// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package com.remipelloux.opensw.lab

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenSwBridgeInstrumentedTest {
    @Test
    fun bridgeReportsVersionedRuntimeAndSessionState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BridgeClient(context).use { client ->
            val bridge = client.connect()
            val identity = JSONObject(bridge.runtimeIdentity)
            val session = JSONObject(bridge.sessionStatus)

            assertEquals("opensw-runtime-identity-v1", identity.getString("schema"))
            assertTrue(identity.getLong("session_generation") >= 0)
            assertEquals("opensw-session-status-v1", session.getString("schema"))
            assertTrue(session.getString("state").isNotBlank())
        }
    }
}
