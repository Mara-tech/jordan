package com.mara.jordan.app.adapter;

import com.mara.jordan.app.model.StatusTextFilter;
import com.mara.jordan.core.dto.JordanParentTaskDTO;
import com.mara.jordan.core.dto.JordanStatusDTO;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * The filters of the Status tab stack up : the search of the toolbar, the text of the filter
 * dialog (JRD-10) and its type and task checkboxes. A status is displayed when it passes them all.
 */
public class ReadStatusAdapterFiltersTest {

    private static final JordanParentTaskDTO TRAINING = JordanParentTaskDTO.builder().taskId(1L).name("training").build();
    private static final JordanParentTaskDTO EVALUATION = JordanParentTaskDTO.builder().taskId(2L).name("evaluation").build();

    private static final JordanStatusDTO[] STATUSES = {
            status(1, "general", TRAINING, "epoch 1 loss = 0.91"),
            status(2, "general", TRAINING, "epoch 2 loss = 0.43"),
            status(3, "general", TRAINING, "checkpoint saved"),
            status(4, "failure", TRAINING, "epoch 3 loss = 0.22"),
            status(5, "general", EVALUATION, "epoch 3 held-out loss = 0.37"),
    };

    private static final Map<String, Boolean> ALL_TYPES = checked("general", true, "failure", true);
    private static final Map<String, Boolean> ALL_TASKS = checked("training", true, "evaluation", true);

    @Test
    public void noFilterKeepsEveryStatusLatestLast() {
        assertDisplayed(apply(null, StatusTextFilter.NONE, ALL_TYPES, ALL_TASKS), 5, 4, 3, 2, 1);
    }

    @Test
    public void dialogTextAloneFilters() {
        assertDisplayed(apply(null, StatusTextFilter.regex("loss = 0\\.[0-4]"), ALL_TYPES, ALL_TASKS), 5, 4, 2);
    }

    @Test
    public void searchAndDialogTextBothApply() {
        assertDisplayed(apply("epoch 3", StatusTextFilter.regex("loss = 0\\.[0-4]"), ALL_TYPES, ALL_TASKS), 5, 4);
        assertDisplayed(apply("held-out", StatusTextFilter.keyword("epoch 3"), ALL_TYPES, ALL_TASKS), 5);
        assertDisplayed(apply("checkpoint", StatusTextFilter.keyword("loss"), ALL_TYPES, ALL_TASKS));
    }

    @Test
    public void typeAndTaskCheckboxesStillApplyWithDialogText() {
        StatusTextFilter lowLoss = StatusTextFilter.regex("loss = 0\\.[0-4]");

        assertDisplayed(apply(null, lowLoss, checked("general", true, "failure", false), ALL_TASKS), 5, 2);
        assertDisplayed(apply(null, lowLoss, ALL_TYPES, checked("training", false, "evaluation", true)), 5);
    }

    @Test
    public void missingDialogTextIsNoFilter() {
        assertDisplayed(apply("epoch 1", null, ALL_TYPES, ALL_TASKS), 1);
    }

    private static List<JordanStatusDTO> apply(String search, StatusTextFilter text, Map<String, Boolean> types, Map<String, Boolean> tasks) {
        return ReadStatusAdapter.applyFilters(search, text, types, tasks, STATUSES);
    }

    private static void assertDisplayed(List<JordanStatusDTO> displayed, long... statusIds) {
        List<Long> expected = new ArrayList<>();
        for (long id : statusIds) {
            expected.add(id);
        }
        List<Long> actual = new ArrayList<>();
        for (JordanStatusDTO status : displayed) {
            actual.add(status.getStatusId());
        }
        assertEquals(expected, actual);
    }

    private static JordanStatusDTO status(long id, String type, JordanParentTaskDTO task, String text) {
        return JordanStatusDTO.builder().statusId(id).type(type).parentTask(task).status(text).timestamp(id).build();
    }

    private static Map<String, Boolean> checked(Object... nameThenChecked) {
        Map<String, Boolean> map = new HashMap<>();
        for (int i = 0; i < nameThenChecked.length; i += 2) {
            map.put((String) nameThenChecked[i], (Boolean) nameThenChecked[i + 1]);
        }
        return map;
    }
}
