package com.mara.jordan.app.api;

import com.mara.jordan.core.dto.JordanStatusDTO;

public interface JordanReadStatusCallback {
    void onStatusLoaded(JordanStatusDTO[] statuses);
    void onStatusLoadingError(String errorMessage);
}
