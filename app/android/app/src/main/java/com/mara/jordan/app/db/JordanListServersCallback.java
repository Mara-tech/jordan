package com.mara.jordan.app.db;

import java.util.List;

public interface JordanListServersCallback {
    void onServersLoaded(List<JordanServer> servers);
    void onServersLoadingError(Throwable error);
}
