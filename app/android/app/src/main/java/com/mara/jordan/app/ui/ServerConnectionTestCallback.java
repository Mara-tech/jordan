package com.mara.jordan.app.ui;

import com.mara.jordan.core.dto.JordanTestDTO;

public interface ServerConnectionTestCallback {
    void onConnectionTestError(Throwable error);
    void onConnectionTestPassed(JordanTestDTO response);
}
