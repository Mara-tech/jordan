package com.mara.jordan.app.ui;

import com.mara.jordan.app.db.JordanServer;

public interface OnServerClickListener {
    void onServerClicked(JordanServer selectedServer);
    void onServerToBeUpdated(JordanServer selectedServer);
}
