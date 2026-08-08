package com.mara.jordan.app.api;

public interface JordanLogoutCallback {

    void onLoggedOut();

    void onLogoutError(String errorMessage);
}
