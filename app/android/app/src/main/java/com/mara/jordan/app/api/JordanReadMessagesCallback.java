package com.mara.jordan.app.api;

import com.mara.jordan.app.model.dto.JordanMessageStateDTO;

public interface JordanReadMessagesCallback {
    void onMessagesLoaded(JordanMessageStateDTO[] messages);
    void onMessagesLoadingError(String errorMessage);
}
