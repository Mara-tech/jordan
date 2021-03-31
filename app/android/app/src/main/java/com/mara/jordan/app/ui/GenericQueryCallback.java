package com.mara.jordan.app.ui;

public interface GenericQueryCallback {
    void onGenericQueryError(String errorMessage);
    void onGenericQueryResponse(String response);
}
