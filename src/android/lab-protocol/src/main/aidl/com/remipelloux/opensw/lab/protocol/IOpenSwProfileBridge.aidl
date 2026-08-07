// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package com.remipelloux.opensw.lab.protocol;

interface IOpenSwProfileBridge {
    String getRuntimeIdentity();
    boolean setPipelineWorkers(int workers);
    boolean clearShaderCache(String titleId);
    boolean stopEmulation();
    boolean startCapture(String titleId, String mode);
    String finishCapture();
}
