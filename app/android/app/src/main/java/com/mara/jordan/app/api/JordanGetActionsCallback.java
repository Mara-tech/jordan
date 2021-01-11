package com.mara.jordan.app.api;

import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;

public interface JordanGetActionsCallback {
    void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions);
    void onActionsLoadingError(String errorMessage);
}
