package com.mara.jordan.app.api;

import com.mara.jordan.core.dto.JordanClientDTO;

public interface JordanGetClientsCallback {
    void onClientsLoaded(JordanClientDTO[] clients);
    void onClientsLoadingError(String errorMessage);
}
