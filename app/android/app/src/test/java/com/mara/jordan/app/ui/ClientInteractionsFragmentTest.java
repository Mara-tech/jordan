package com.mara.jordan.app.ui;

import android.os.Bundle;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.JordanTaskModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

/**
 * The Task screen through the rotations of JRD-13 : a rotation recreates the activity, the screen
 * and its tab, which must come back as the tab selected before, its menu item checked, and once only.
 * The calls the tabs make go nowhere (no server is set) and fail, which is part of the lifecycle.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ClientInteractionsFragmentTest {

    private static final String SCREEN_TAG = "task_screen";

    /**
     * Holds the screen the way the navigation graph does, with the arguments it receives.
     */
    public static class HostActivity extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_JordanApp);
            super.onCreate(savedInstanceState);
            if (savedInstanceState == null) {
                Bundle args = new Bundle();
                args.putLong(JordanTaskModel.CLIENT_ID, 1L);
                args.putString(JordanTaskModel.CLIENT_NAME, "training");
                ClientInteractionsFragment screen = new ClientInteractionsFragment();
                screen.setArguments(args);
                getSupportFragmentManager().beginTransaction()
                        .add(android.R.id.content, screen, SCREEN_TAG)
                        .commitNow();
            }
        }
    }

    private ActivityController<HostActivity> controller;

    @Before
    public void openTaskScreen() {
        controller = Robolectric.buildActivity(HostActivity.class).setup();
        idle();
    }

    @After
    public void closeTaskScreen() {
        if (controller != null) {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void actionsIsTheFirstTab() {
        assertTab(TaskAndActionsFragment.class, R.id.client_interaction_action);
    }

    @Test
    public void rotationKeepsTheSelectedTab() {
        select(R.id.client_interaction_status);
        assertTab(ReadStatusFragment.class, R.id.client_interaction_status);

        rotate();

        assertTab(ReadStatusFragment.class, R.id.client_interaction_status);
    }

    @Test
    public void secondRotationKeepsTheSelectedTab() {
        select(R.id.client_interaction_status);

        rotate();
        rotate();

        assertTab(ReadStatusFragment.class, R.id.client_interaction_status);
    }

    @Test
    public void everyTabSurvivesRotations() {
        assertSurvivesRotations(R.id.client_interaction_status, ReadStatusFragment.class);
        assertSurvivesRotations(R.id.client_interaction_action, TaskAndActionsFragment.class);
        assertSurvivesRotations(R.id.client_interaction_messages_state, MessagesStateFragment.class);
        assertSurvivesRotations(R.id.client_interaction_metrics, MetricsFragment.class);
    }

    @Test
    public void restoredTabSharesTheModelOfTheScreen() {
        select(R.id.client_interaction_metrics);

        rotate();

        ClientInteractionsFragment screen = screen();
        assertSame(screen.getTaskModel(), ClientInteractionsFragment.taskModelOf(screen.getDisplayedTab()));
    }

    @Test
    public void switchingTabsStaysOutOfTheBackStack() {
        select(R.id.client_interaction_status);
        select(R.id.client_interaction_messages_state);
        select(R.id.client_interaction_metrics);

        assertEquals(0, screen().getChildFragmentManager().getBackStackEntryCount());
        assertEquals(0, controller.get().getSupportFragmentManager().getBackStackEntryCount());
    }

    private void assertSurvivesRotations(int menuItem, Class<? extends Fragment> tab) {
        select(menuItem);
        rotate();
        assertTab(tab, menuItem);
        rotate();
        assertTab(tab, menuItem);
    }

    /**
     * The tab displayed, the only one the screen holds, is the one its menu shows checked.
     */
    private void assertTab(Class<? extends Fragment> tab, int menuItem) {
        ClientInteractionsFragment screen = screen();
        assertEquals(1, screen.getChildFragmentManager().getFragments().size());
        assertEquals(tab, screen.getDisplayedTab().getClass());
        assertEquals(menuItem, bottomMenu().getSelectedItemId());
    }

    private void select(int menuItem) {
        bottomMenu().setSelectedItemId(menuItem);
        idle();
    }

    private void rotate() {
        controller.recreate();
        idle();
    }

    private ClientInteractionsFragment screen() {
        return (ClientInteractionsFragment) controller.get().getSupportFragmentManager().findFragmentByTag(SCREEN_TAG);
    }

    private BottomNavigationView bottomMenu() {
        return screen().requireView().findViewById(R.id.bottom_navigation);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
