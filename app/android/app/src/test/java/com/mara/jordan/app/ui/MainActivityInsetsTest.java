package com.mara.jordan.app.ui;

import android.os.Looper;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mara.jordan.app.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The main screen under the system bars of JRD-14 : from API 35 the window is drawn edge to edge,
 * and the insets the system dispatches are all that keeps the toolbar out from under the status
 * bar. The test dispatches those insets itself, as a Pixel 8 on API 35 does — Robolectric runs API 35
 * on JDK 21 only, and CI builds on 17, so the screen runs on 34 like the other tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityInsetsTest {

    private static final int STATUS_BAR = 96;
    private static final int NAVIGATION_BAR = 48;

    private ActivityController<MainActivity> controller;
    private View root;
    private View appBar;
    private View content;
    private Toolbar toolbar;

    @Before
    public void openMainScreen() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        root = activity.findViewById(R.id.main_root);
        appBar = activity.findViewById(R.id.app_bar);
        content = activity.findViewById(R.id.content_main);
        toolbar = activity.findViewById(R.id.toolbar);
        idle();
    }

    @After
    public void closeMainScreen() {
        if (controller != null) {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void toolbarStartsBelowTheStatusBar() {
        dispatch(Insets.of(0, STATUS_BAR, 0, NAVIGATION_BAR), Insets.NONE);

        assertEquals(STATUS_BAR, appBar.getPaddingTop());
        int[] onScreen = new int[2];
        toolbar.getLocationInWindow(onScreen);
        assertTrue("toolbar top at " + onScreen[1] + ", under a status bar of " + STATUS_BAR,
                onScreen[1] >= STATUS_BAR);
    }

    @Test
    public void contentStaysAboveTheNavigationBar() {
        dispatch(Insets.of(0, STATUS_BAR, 0, NAVIGATION_BAR), Insets.NONE);

        assertEquals(NAVIGATION_BAR, content.getPaddingBottom());
        assertEquals(0, content.getPaddingTop());
    }

    @Test
    public void sidesClearALandscapeCutout() {
        dispatch(Insets.of(0, STATUS_BAR, 0, 0), Insets.of(80, 0, 0, 0));

        assertEquals(80, appBar.getPaddingLeft());
        assertEquals(80, content.getPaddingLeft());
        assertEquals(0, content.getPaddingRight());
    }

    @Test
    public void nothingMovesWithoutInsets() {
        dispatch(Insets.NONE, Insets.NONE);

        assertEquals(0, appBar.getPaddingTop());
        assertEquals(0, content.getPaddingBottom());
    }

    @Test
    public void newInsetsReplaceThePreviousOnes() {
        dispatch(Insets.of(0, STATUS_BAR, 0, NAVIGATION_BAR), Insets.NONE);
        dispatch(Insets.of(0, 24, 0, 0), Insets.NONE);

        assertEquals(24, appBar.getPaddingTop());
        assertEquals(0, content.getPaddingBottom());
    }

    private void dispatch(Insets systemBars, Insets cutout) {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
                .build();
        ViewCompat.dispatchApplyWindowInsets(root, insets);
        root.requestLayout();
        idle();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
