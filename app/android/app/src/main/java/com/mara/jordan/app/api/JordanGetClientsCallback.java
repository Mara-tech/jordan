package com.mara.jordan.app.api;

import com.mara.jordan.app.model.dto.JordanClientDTO;

public interface JordanGetClientsCallback {
    void onClientsLoaded(JordanClientDTO[] clients);
    void onClientsLoadingError(String errorMessage);
}
