package com.mara.jordan.app.api;

import com.mara.jordan.app.model.dto.JordanStatusDTO;

public interface JordanReadStatusCallback {
    void onStatusLoaded(JordanStatusDTO[] statuses);
    void onStatusLoadingError(String errorMessage);
}
