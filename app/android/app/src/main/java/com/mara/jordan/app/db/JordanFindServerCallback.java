package com.mara.jordan.app.db;

import androidx.annotation.Nullable;

public interface JordanFindServerCallback {

    /**
     * @param server the stored server, or {@code null} when this URL is not in the local database
     */
    void onServerFound(@Nullable JordanServer server);
}
