package com.mara.jordan.app.ui;

import com.mara.jordan.app.db.JordanServer;

public interface ServerAddOrUpdateCallback {
    void addServer(JordanServer entity);
    void updateServer(JordanServer entity);
}
