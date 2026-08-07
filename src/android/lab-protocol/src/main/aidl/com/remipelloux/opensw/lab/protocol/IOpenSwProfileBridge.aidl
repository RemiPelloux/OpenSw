// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package com.remipelloux.opensw.lab.protocol;

interface IOpenSwProfileBridge {
    String getRuntimeIdentity();
    String getSessionStatus();
    boolean setPipelineWorkers(int workers);
    boolean clearShaderCache(String titleId);
    boolean launchGame(String gameUri, String expectedTitleId, long timeoutMs);
    boolean pauseEmulation(long timeoutMs);
    boolean resumeEmulation(long timeoutMs);
    boolean stopEmulation(long timeoutMs);
    boolean startReplay(String replayJson);
    boolean cancelReplay();
    boolean startCapture(String titleId, String mode);
    String finishCapture();
    String getCheats();
    boolean setCheatEnabled(String name, boolean enabled);
}
