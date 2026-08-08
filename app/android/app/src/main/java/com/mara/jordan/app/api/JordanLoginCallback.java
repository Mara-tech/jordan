package com.mara.jordan.app.api;

import com.mara.jordan.core.dto.JordanAdminSessionDTO;

public interface JordanLoginCallback {

    void onLoggedIn(JordanAdminSessionDTO session);

    void onLoginError(String errorMessage);
}
