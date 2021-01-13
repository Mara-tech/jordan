package com.mara.jordan.app.ui;

import com.mara.jordan.app.model.dto.JordanTestDTO;

public interface ServerConnectionTestCallback {
    void onConnectionTestError(Throwable error);
    void onConnectionTestPassed(JordanTestDTO response);
}
