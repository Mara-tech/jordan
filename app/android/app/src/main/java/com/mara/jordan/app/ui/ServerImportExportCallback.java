package com.mara.jordan.app.ui;

import com.mara.jordan.app.db.JordanServer;

public interface ServerImportExportCallback {
    void onServerExported(int count);

    void onServerImportParsed(JordanServer[] servers);

    void onServerImportError(String... errorMessages);
}
