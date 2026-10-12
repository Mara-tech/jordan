package com.mara.jordan.app.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.core.dto.JordanParentTaskDTO;
import com.mara.jordan.core.dto.JordanStatusDTO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

/**
 * The text field of the filter dialog of the Status tab (JRD-10), driven the way a user does :
 * typed, applied, refused when the regular expression is invalid, kept across a refresh. And the
 * message shown in place of an empty list, which tells « nothing was read » from « the filters
 * hide everything » (JRD-32). And every one of those filters through a rotation, which recreates
 * the tab (JRD-31). The statuses come from a model that serves them without a server ; a rotation
 * recreates that model too, which reads the same {@link ReadStatusFragmentTest#served}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReadStatusFragmentTest {

    private static final String SCREEN_TAG = "task_screen";
    private static final JordanParentTaskDTO TRAINING = JordanParentTaskDTO.builder().taskId(1L).name("training").build();
    private static final JordanParentTaskDTO EVALUATION = JordanParentTaskDTO.builder().taskId(2L).name("evaluation").build();

    /**
     * What the next reading of the statuses returns.
     */
    private static JordanStatusDTO[] served;

    /**
     * Reads {@link ReadStatusFragmentTest#served} in place of the server, calling back in the
     * order the API does : the callers, then the model itself.
     */
    public static class ServedTaskModel extends JordanTaskModel {
        public ServedTaskModel(Context ctx) {
            super(ctx, 1L);
        }

        @Override
        public void readStatus(int lineCount, JordanReadStatusCallback... callbacks) {
            for (JordanReadStatusCallback callback : callbacks) {
                callback.onStatusLoaded(served);
            }
            onStatusLoaded(served);
        }
    }

    public static class ServedScreen extends ClientInteractionsFragment {
        private JordanTaskModel model;

        @Override
        public JordanTaskModel getTaskModel() {
            if (model == null) {
                model = new ServedTaskModel(requireContext());
            }
            return model;
        }
    }

    public static class HostActivity extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_JordanApp);
            super.onCreate(savedInstanceState);
            if (savedInstanceState == null) {
                Bundle args = new Bundle();
                args.putLong(JordanTaskModel.CLIENT_ID, 1L);
                args.putString(JordanTaskModel.CLIENT_NAME, "training");
                ServedScreen screen = new ServedScreen();
                screen.setArguments(args);
                getSupportFragmentManager().beginTransaction()
                        .add(android.R.id.content, screen, SCREEN_TAG)
                        .commitNow();
            }
        }
    }

    private ActivityController<HostActivity> controller;

    @Before
    public void openStatusTab() {
        served = statuses("epoch 1 loss = 0.91", "epoch 2 loss = 0.43", "checkpoint saved", "epoch 3 loss = 0.22");
        controller = Robolectric.buildActivity(HostActivity.class).setup();
        idle();
        bottomMenu().setSelectedItemId(R.id.client_interaction_status);
        idle();
    }

    @After
    public void closeStatusTab() {
        if (controller != null) {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void everyStatusIsDisplayedAtFirst() {
        assertDisplayed("epoch 3 loss = 0.22", "checkpoint saved", "epoch 2 loss = 0.43", "epoch 1 loss = 0.91");
    }

    @Test
    public void keywordOfTheDialogFiltersTheList() {
        AlertDialog dialog = openFilterDialog();
        textField(dialog).setText("LOSS");

        apply(dialog);

        assertFalse(dialog.isShowing());
        assertDisplayed("epoch 3 loss = 0.22", "epoch 2 loss = 0.43", "epoch 1 loss = 0.91");
    }

    @Test
    public void regexOfTheDialogFiltersTheList() {
        AlertDialog dialog = openFilterDialog();
        textField(dialog).setText("loss = 0\\.[0-4]");
        regexBox(dialog).setChecked(true);

        apply(dialog);

        assertFalse(dialog.isShowing());
        assertDisplayed("epoch 3 loss = 0.22", "epoch 2 loss = 0.43");
    }

    @Test
    public void invalidRegexKeepsTheDialogOpenAndSaysWhy() {
        applyText("epoch", false);

        AlertDialog dialog = openFilterDialog();
        textField(dialog).setText("(epoch");
        regexBox(dialog).setChecked(true);
        apply(dialog);

        assertTrue(dialog.isShowing());
        TextView error = textError(dialog);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertTrue(error.getText().toString(), error.getText().toString().contains("missing closing )"));
        assertDisplayed("epoch 3 loss = 0.22", "epoch 2 loss = 0.43", "epoch 1 loss = 0.91");

        textField(dialog).setText("^(epoch [23])");
        assertEquals(View.GONE, error.getVisibility());
        apply(dialog);

        assertFalse(dialog.isShowing());
        assertDisplayed("epoch 3 loss = 0.22", "epoch 2 loss = 0.43");
    }

    @Test
    public void uncheckingRegexHidesItsError() {
        AlertDialog dialog = openFilterDialog();
        textField(dialog).setText("(epoch");
        regexBox(dialog).setChecked(true);
        apply(dialog);
        assertEquals(View.VISIBLE, textError(dialog).getVisibility());

        regexBox(dialog).setChecked(false);

        assertEquals(View.GONE, textError(dialog).getVisibility());
    }

    @Test
    public void dialogTextIsKeptAcrossARefresh() {
        applyText("loss", false);

        served = statuses("epoch 4 loss = 0.19", "evaluation started", "epoch 5 loss = 0.17");
        tab().refreshContent();
        idle();

        assertDisplayed("epoch 5 loss = 0.17", "epoch 4 loss = 0.19");
    }

    @Test
    public void reopenedDialogShowsTheAppliedText() {
        applyText("^epoch [12]", true);

        AlertDialog dialog = openFilterDialog();

        assertEquals("^epoch [12]", textField(dialog).getText().toString());
        assertTrue(regexBox(dialog).isChecked());
        assertEquals(View.GONE, textError(dialog).getVisibility());
    }

    @Test
    public void cancelLeavesTheTextAsItWas() {
        applyText("epoch", false);

        AlertDialog dialog = openFilterDialog();
        textField(dialog).setText("checkpoint");
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
        idle();

        assertDisplayed("epoch 3 loss = 0.22", "epoch 2 loss = 0.43", "epoch 1 loss = 0.91");
        assertEquals("epoch", textField(openFilterDialog()).getText().toString());
    }

    @Test
    public void emptyTextRemovesTheFilter() {
        applyText("checkpoint", false);

        applyText("", true);

        assertDisplayed("epoch 3 loss = 0.22", "checkpoint saved", "epoch 2 loss = 0.43", "epoch 1 loss = 0.91");
    }

    @Test
    public void noMessageWhileStatusesAreDisplayed() {
        assertEquals(View.GONE, emptyView().getVisibility());
    }

    @Test
    public void filtersHidingEveryStatusSaySoInsteadOfABlankScreen() {
        applyText("no status contains this", false);

        assertDisplayed();
        assertHiddenByFilters(4);
    }

    @Test
    public void serverWithoutStatusStillSaysThereIsNone() {
        applyText("no status contains this", false);

        served = statuses();
        tab().refreshContent();
        idle();

        assertDisplayed();
        assertEquals(View.VISIBLE, emptyView().getVisibility());
        assertEquals(string(R.string.no_status_to_display), emptyView().getText().toString());
    }

    @Test
    public void messageFollowsTheSearch() {
        tab().setCurrentSearchQuery("no status contains this");
        idle();
        assertHiddenByFilters(4);

        tab().setCurrentSearchQuery("checkpoint");
        idle();
        assertDisplayed("checkpoint saved");
        assertEquals(View.GONE, emptyView().getVisibility());
    }

    @Test
    public void messageFollowsTheDialog() {
        applyText("^evaluation", true);
        assertHiddenByFilters(4);

        applyText("", false);
        assertEquals(View.GONE, emptyView().getVisibility());
    }

    @Test
    public void messageFollowsEachRefresh() {
        applyText("checkpoint", false);
        assertEquals(View.GONE, emptyView().getVisibility());

        served = statuses("epoch 4 loss = 0.19");
        tab().refreshContent();
        idle();
        assertHiddenByFilters(1);

        served = statuses("epoch 5 loss = 0.17", "checkpoint saved");
        tab().refreshContent();
        idle();
        assertDisplayed("checkpoint saved");
        assertEquals(View.GONE, emptyView().getVisibility());
    }

    @Test
    public void dialogKeywordSurvivesARotation() {
        applyText("checkpoint", false);

        rotate();

        assertDisplayed("checkpoint saved");
        AlertDialog dialog = openFilterDialog();
        assertEquals("checkpoint", textField(dialog).getText().toString());
        assertFalse(regexBox(dialog).isChecked());
    }

    @Test
    public void dialogRegexSurvivesARotation() {
        applyText("^epoch [12]", true);

        rotate();

        assertDisplayed("epoch 2 loss = 0.43", "epoch 1 loss = 0.91");
        AlertDialog dialog = openFilterDialog();
        assertEquals("^epoch [12]", textField(dialog).getText().toString());
        assertTrue(regexBox(dialog).isChecked());
    }

    @Test
    public void uncheckedTypesAndTasksSurviveRotations() {
        served = new JordanStatusDTO[]{
                status(1, "epoch 1 loss = 0.91", "metric", TRAINING),
                status(2, "evaluation started", "general", EVALUATION),
                status(3, "checkpoint saved", "general", TRAINING),
                status(4, "held-out loss = 0.66", "metric", EVALUATION)};
        tab().refreshContent();
        idle();
        AlertDialog dialog = openFilterDialog();
        setChecked(dialog, R.id.status_filter_type_list, "metric", false);
        setChecked(dialog, R.id.status_filter_task_list, "evaluation", false);
        apply(dialog);
        assertDisplayed("checkpoint saved");

        rotate();
        assertDisplayed("checkpoint saved");
        rotate();
        assertDisplayed("checkpoint saved");

        AlertDialog reopened = openFilterDialog();
        assertFalse(isChecked(reopened, R.id.status_filter_type_list, "metric"));
        assertTrue(isChecked(reopened, R.id.status_filter_type_list, "general"));
        assertFalse(isChecked(reopened, R.id.status_filter_task_list, "evaluation"));
        assertTrue(isChecked(reopened, R.id.status_filter_task_list, "training"));
    }

    @Test
    public void toolbarSearchSurvivesARotationAndStaysShown() {
        searchItem().expandActionView();
        idle();
        searchView().setQuery("checkpoint", false);
        idle();
        assertDisplayed("checkpoint saved");

        rotate();

        assertDisplayed("checkpoint saved");
        assertTrue(searchItem().isActionViewExpanded());
        assertEquals("checkpoint", searchView().getQuery().toString());
    }

    @Test
    public void closedToolbarSearchStaysClosedAfterARotation() {
        searchItem().expandActionView();
        idle();
        searchView().setQuery("checkpoint", false);
        idle();
        searchItem().collapseActionView();
        idle();
        assertDisplayed("epoch 3 loss = 0.22", "checkpoint saved", "epoch 2 loss = 0.43", "epoch 1 loss = 0.91");

        rotate();

        assertDisplayed("epoch 3 loss = 0.22", "checkpoint saved", "epoch 2 loss = 0.43", "epoch 1 loss = 0.91");
        assertFalse(searchItem().isActionViewExpanded());
    }

    private void rotate() {
        controller.recreate();
        idle();
    }

    /**
     * The search of the toolbar, as the action bar of the activity holds it.
     */
    private MenuItem searchItem() {
        Toolbar actionBar = controller.get().findViewById(androidx.appcompat.R.id.action_bar);
        return actionBar.getMenu().findItem(R.id.status_search);
    }

    private SearchView searchView() {
        return (SearchView) searchItem().getActionView();
    }

    /**
     * Ticks or unticks a checkbox of the dialog the way a tap does, through the view its list
     * adapter makes for it.
     */
    private static void setChecked(AlertDialog dialog, int listId, String name, boolean checked) {
        checkBox(dialog, listId, name).setChecked(checked);
    }

    private static boolean isChecked(AlertDialog dialog, int listId, String name) {
        return checkBox(dialog, listId, name).isChecked();
    }

    private static CheckBox checkBox(AlertDialog dialog, int listId, String name) {
        ListView list = dialog.findViewById(listId);
        ListAdapter adapter = list.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (name.equals(adapter.getItem(i))) {
                return adapter.getView(i, null, list).findViewById(R.id.filter_item_check);
            }
        }
        fail("No checkbox " + name + " in the dialog");
        return null;
    }

    private void applyText(String text, boolean regex) {
        AlertDialog dialog = openFilterDialog();
        textField(dialog).setText(text);
        regexBox(dialog).setChecked(regex);
        apply(dialog);
        assertFalse(dialog.isShowing());
    }

    private AlertDialog openFilterDialog() {
        tab().onOptionsItemSelected(new RoboMenuItem(R.id.filter_status));
        idle();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private static void apply(AlertDialog dialog) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
    }

    private static EditText textField(AlertDialog dialog) {
        return dialog.findViewById(R.id.status_filter_text);
    }

    private static CheckBox regexBox(AlertDialog dialog) {
        return dialog.findViewById(R.id.status_filter_text_regex);
    }

    private static TextView textError(AlertDialog dialog) {
        return dialog.findViewById(R.id.status_filter_text_error);
    }

    /**
     * @param texts the statuses displayed, top to bottom
     */
    private void assertDisplayed(String... texts) {
        ListView list = tab().requireView().findViewById(R.id.read_status_list);
        ListAdapter adapter = list.getAdapter();
        List<String> displayed = new ArrayList<>();
        for (int i = 0; i < adapter.getCount(); i++) {
            displayed.add(((JordanStatusDTO) adapter.getItem(i)).getStatus());
        }
        assertEquals(Arrays.asList(texts), displayed);
    }

    private void assertHiddenByFilters(int read) {
        assertEquals(View.VISIBLE, emptyView().getVisibility());
        String expected = tab().getResources().getQuantityString(R.plurals.statuses_hidden_by_filters, read, read);
        assertEquals(expected, emptyView().getText().toString());
    }

    private TextView emptyView() {
        return tab().requireView().findViewById(R.id.empty_view);
    }

    private String string(int id) {
        return tab().getString(id);
    }

    /**
     * @param texts oldest first, the order the server returns them in
     */
    private static JordanStatusDTO[] statuses(String... texts) {
        JordanStatusDTO[] statuses = new JordanStatusDTO[texts.length];
        for (int i = 0; i < texts.length; i++) {
            statuses[i] = JordanStatusDTO.builder().statusId(i + 1).type("general").parentTask(TRAINING)
                    .status(texts[i]).timestamp(1_000L + i).build();
        }
        return statuses;
    }

    private static JordanStatusDTO status(int id, String text, String type, JordanParentTaskDTO task) {
        return JordanStatusDTO.builder().statusId(id).type(type).parentTask(task)
                .status(text).timestamp(1_000L + id).build();
    }

    private ReadStatusFragment tab() {
        ClientInteractionsFragment screen = (ClientInteractionsFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag(SCREEN_TAG);
        return (ReadStatusFragment) screen.getDisplayedTab();
    }

    private BottomNavigationView bottomMenu() {
        ClientInteractionsFragment screen = (ClientInteractionsFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag(SCREEN_TAG);
        return screen.requireView().findViewById(R.id.bottom_navigation);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
