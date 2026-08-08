package com.mara.jordan.app.ui;

/**
 * A screen able to reload what it displays, typically once an admin session has been opened
 * and the calls it made before are worth retrying.
 */
public interface JordanRefreshable {

    void refreshContent();
}
