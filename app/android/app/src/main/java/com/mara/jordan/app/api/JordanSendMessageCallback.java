package com.mara.jordan.app.api;

public interface JordanSendMessageCallback {
    void onMessageSent(long messageId);
    void onMessageSendingError(String errorMessage);
}
