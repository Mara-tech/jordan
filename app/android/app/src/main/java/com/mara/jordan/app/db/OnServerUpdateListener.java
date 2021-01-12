package com.mara.jordan.app.db;

public interface OnServerUpdateListener {
    void onServerAdded(JordanServer entity);
    void onServerUpdated(JordanServer entity);
    void onServerDeleted(JordanServer entity);
    void onServerUpdateError(JordanServer entity, Throwable error);
}
