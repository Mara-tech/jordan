package com.mara.jordan.app.ui;

public interface ClientDeletionCallback {
    void onClientDeletionError(String errorMessage);
    void onClientDeleted();
}
