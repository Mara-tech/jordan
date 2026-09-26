package com.mara.jordan.core;

import com.mara.jordan.core.dto.JordanMetricPointDTO;
import com.mara.jordan.core.dto.JordanMetricSeriesDTO;
import com.mara.jordan.core.dto.JordanParentTaskDTO;
import com.mara.jordan.core.dto.JordanStatusDTO;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MetricUtilsTest {

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static JordanMetricPointDTO point(double value, Double step, long timestamp) {
        return JordanMetricPointDTO.builder().value(value).step(step).timestamp(timestamp).build();
    }

    private static JordanMetricSeriesDTO series(String name, long taskId, String taskName, JordanMetricPointDTO... points) {
        return JordanMetricSeriesDTO.builder()
                .name(name)
                .parentTask(JordanParentTaskDTO.builder().taskId(taskId).name(taskName).build())
                .points(Arrays.asList(points))
                .build();
    }

    private static final JordanMetricSeriesDTO HELD_OUT = series("held-out loss", 8, "fine_tune",
            point(0.6653, 0.0, 1000), point(0.6677, 1.0, 1083));
    private static final JordanMetricSeriesDTO TRAINING = series("training loss", 8, "fine_tune",
            point(0.7772, 1.0, 1083));
    private static final JordanMetricSeriesDTO THROUGHPUT = series("throughput", 7, "training",
            point(120, null, 990), point(118, null, 1050));

    // -------------------------------------------------------------------------
    // describe / formatNumber
    // -------------------------------------------------------------------------

    @Test
    public void testDescribeWithStep() {
        assertEquals("held-out loss = 0.6648 (step 3)", MetricUtils.describe("held-out loss", 0.6648, 3.0));
    }

    @Test
    public void testDescribeWithoutStep() {
        assertEquals("throughput = 120", MetricUtils.describe("throughput", 120, null));
    }

    @Test
    public void testFormatNumberDropsTheNoiseOfDoubles() {
        assertEquals("3", MetricUtils.formatNumber(3.0));
        assertEquals("2.5", MetricUtils.formatNumber(2.5));
        assertEquals("0.6648", MetricUtils.formatNumber(0.6648));
        assertEquals("0.0000001", MetricUtils.formatNumber(1e-7));
        assertEquals("-12", MetricUtils.formatNumber(-12.0));
        assertEquals("0", MetricUtils.formatNumber(0.0));
        assertEquals("0", MetricUtils.formatNumber(-0.0));
    }

    // -------------------------------------------------------------------------
    // names / select
    // -------------------------------------------------------------------------

    @Test
    public void testNamesAreDistinctInTheOrderTheyCome() {
        JordanMetricSeriesDTO otherTaskHeldOut = series("held-out loss", 9, "other", point(0.9, 1.0, 1000));
        assertEquals(Arrays.asList("held-out loss", "training loss", "throughput"),
                MetricUtils.names(Arrays.asList(HELD_OUT, TRAINING, otherTaskHeldOut, THROUGHPUT)));
    }

    @Test
    public void testSelectKeepsEveryTaskSeriesOfAName() {
        JordanMetricSeriesDTO otherTaskHeldOut = series("held-out loss", 9, "other", point(0.9, 1.0, 1000));
        List<JordanMetricSeriesDTO> all = Arrays.asList(HELD_OUT, TRAINING, otherTaskHeldOut);
        assertEquals(Arrays.asList(HELD_OUT, otherTaskHeldOut),
                MetricUtils.select(all, Collections.singleton("held-out loss")));
        assertTrue(MetricUtils.select(all, Collections.<String>emptySet()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // canPlotAgainstStep
    // -------------------------------------------------------------------------

    @Test
    public void testSeriesWithStepsCanBeDrawnAgainstSteps() {
        assertTrue(MetricUtils.canPlotAgainstStep(Arrays.asList(HELD_OUT, TRAINING)));
    }

    @Test
    public void testOneSeriesWithoutStepsRulesStepsOut() {
        assertFalse(MetricUtils.canPlotAgainstStep(Arrays.asList(HELD_OUT, THROUGHPUT)));
    }

    @Test
    public void testOnePointWithoutStepRulesStepsOut() {
        JordanMetricSeriesDTO mixed = series("loss", 7, "training", point(1, 1.0, 1000), point(0.5, null, 1060));
        assertFalse(MetricUtils.canPlotAgainstStep(Collections.singletonList(mixed)));
    }

    @Test
    public void testNothingToDrawIsNotDrawnAgainstSteps() {
        assertFalse(MetricUtils.canPlotAgainstStep(Collections.<JordanMetricSeriesDTO>emptyList()));
        assertFalse(MetricUtils.canPlotAgainstStep(Collections.singletonList(series("empty", 7, "training"))));
    }

    // -------------------------------------------------------------------------
    // timeOrigin
    // -------------------------------------------------------------------------

    @Test
    public void testTimeOriginIsTheEarliestPoint() {
        assertEquals(990L, MetricUtils.timeOrigin(Arrays.asList(HELD_OUT, THROUGHPUT)));
    }

    @Test
    public void testTimeOriginWithoutPoints() {
        assertEquals(0L, MetricUtils.timeOrigin(Collections.<JordanMetricSeriesDTO>emptyList()));
    }

    // -------------------------------------------------------------------------
    // label
    // -------------------------------------------------------------------------

    @Test
    public void testLabelIsTheNameWhenItIsUnambiguous() {
        List<JordanMetricSeriesDTO> all = Arrays.asList(HELD_OUT, TRAINING, THROUGHPUT);
        assertEquals("held-out loss", MetricUtils.label(HELD_OUT, all));
    }

    @Test
    public void testLabelNamesTheTaskWhenTwoTasksShareAName() {
        JordanMetricSeriesDTO otherTaskHeldOut = series("held-out loss", 9, "other", point(0.9, 1.0, 1000));
        List<JordanMetricSeriesDTO> all = Arrays.asList(HELD_OUT, TRAINING, otherTaskHeldOut);
        assertEquals("held-out loss [fine_tune]", MetricUtils.label(HELD_OUT, all));
        assertEquals("held-out loss [other]", MetricUtils.label(otherTaskHeldOut, all));
        assertEquals("training loss", MetricUtils.label(TRAINING, all));
    }

    // -------------------------------------------------------------------------
    // what the server sends
    // -------------------------------------------------------------------------

    @Test
    public void testSeriesReadFromTheServer() {
        String json = "{\"name\": \"held-out loss\", \"parentTask\": {\"taskId\": 8, \"name\": \"fine_tune\"},"
                + " \"points\": [{\"statusId\": 1, \"value\": 0.6653, \"step\": 0.0, \"timestamp\": 1000},"
                + " {\"statusId\": 2, \"value\": 0.6648, \"step\": null, \"timestamp\": 1060}]}";
        JordanMetricSeriesDTO read = SerDeUtils.deserialize(json, JordanMetricSeriesDTO.class);
        assertEquals("fine_tune", read.getParentTask().getName());
        assertEquals(Double.valueOf(0.0), read.getPoints().get(0).getStep());
        assertNull(read.getPoints().get(1).getStep());
        assertEquals(1060L, read.getPoints().get(1).getTimestamp());
    }

    @Test
    public void testStatusCarriesItsMetric() {
        String json = "{\"statusId\": 1, \"type\": \"metric\", \"status\": \"loss = 0.5\", \"timestamp\": 1000,"
                + " \"metric\": {\"name\": \"loss\", \"value\": 0.5, \"step\": null}}";
        JordanStatusDTO read = SerDeUtils.deserialize(json, JordanStatusDTO.class);
        assertEquals(JordanConstants.STATUS_TYPE_METRIC, read.getType());
        assertEquals("loss", read.getMetric().getName());
        assertNull(read.getMetric().getStep());
    }

    @Test
    public void testOtherStatusHasNoMetric() {
        String json = "{\"statusId\": 1, \"type\": \"general\", \"status\": \"working\", \"timestamp\": 1000, \"metric\": null}";
        assertNull(SerDeUtils.deserialize(json, JordanStatusDTO.class).getMetric());
    }
}
