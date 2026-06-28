package com.mara.jordan.app.api;

import com.mara.jordan.core.dto.JordanActionDefinitionWithTaskDTO;

public interface JordanGetActionsCallback {
    void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions);
    void onActionsLoadingError(String errorMessage);
}
