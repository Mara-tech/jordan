package com.mara.jordan.app.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps the app bar and the screen below it clear of the system bars (JRD-14).
 * <p>
 * From API 35 on, an app targeting 35 or later is drawn edge to edge whatever its theme says: the
 * window no longer stops at the status bar, and the toolbar slides beneath it, its buttons out of
 * reach. The insets the system reports become padding instead — the top one on the app bar, whose
 * background then fills the area behind the status bar while its buttons start below it; the sides
 * and the bottom on the content, clear of the navigation bar and of a display cutout.
 * <p>
 * Below API 35 the window still fits the system bars itself, the insets reaching these views are
 * zero, and nothing moves.
 */
final class SystemBarInsets {

    /**
     * What is kept clear: the status and navigation bars, and a cutout that sticks out past them.
     */
    static final int TYPES = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

    private SystemBarInsets() {
    }

    /**
     * Pads {@code appBar} and {@code content} with the insets {@code root} receives, on top of the
     * padding they declare, and consumes the insets so that no child applies them a second time.
     */
    static void apply(View root, final View appBar, final View content) {
        final int appBarLeft = appBar.getPaddingLeft();
        final int appBarTop = appBar.getPaddingTop();
        final int appBarRight = appBar.getPaddingRight();
        final int appBarBottom = appBar.getPaddingBottom();
        final int contentLeft = content.getPaddingLeft();
        final int contentTop = content.getPaddingTop();
        final int contentRight = content.getPaddingRight();
        final int contentBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(TYPES);
            appBar.setPadding(appBarLeft + bars.left, appBarTop + bars.top,
                    appBarRight + bars.right, appBarBottom);
            content.setPadding(contentLeft + bars.left, contentTop,
                    contentRight + bars.right, contentBottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
