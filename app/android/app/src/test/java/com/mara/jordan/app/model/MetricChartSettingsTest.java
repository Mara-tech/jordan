package com.mara.jordan.app.model;

import com.mara.jordan.app.model.MetricChartSettings.XAxisChoice;
import com.mara.jordan.core.dto.JordanMetricPointDTO;
import com.mara.jordan.core.dto.JordanMetricSeriesDTO;
import com.mara.jordan.core.dto.JordanParentTaskDTO;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetricChartSettingsTest {

    private static JordanMetricPointDTO point(double value, Double step, long timestamp) {
        return JordanMetricPointDTO.builder().value(value).step(step).timestamp(timestamp).build();
    }

    private static JordanMetricSeriesDTO series(String name, JordanMetricPointDTO... points) {
        return JordanMetricSeriesDTO.builder()
                .name(name)
                .parentTask(JordanParentTaskDTO.builder().taskId(8L).name("fine_tune").build())
                .points(Arrays.asList(points))
                .build();
    }

    private static final JordanMetricSeriesDTO HELD_OUT = series("held-out loss", point(0.6653, 0.0, 1000), point(0.6677, 1.0, 1083));
    private static final JordanMetricSeriesDTO TRAINING = series("training loss", point(0.7772, 1.0, 1083));
    private static final JordanMetricSeriesDTO THROUGHPUT = series("throughput", point(120, null, 990));
    private static final List<JordanMetricSeriesDTO> ALL = Arrays.asList(HELD_OUT, TRAINING, THROUGHPUT);
    private static final List<String> ALL_NAMES = Arrays.asList("held-out loss", "training loss", "throughput");

    @Test
    public void everyMetricIsShownAtFirst() {
        assertEquals(ALL, new MetricChartSettings().shownSeries(ALL));
    }

    @Test
    public void uncheckedMetricsAreHidden() {
        MetricChartSettings settings = new MetricChartSettings();
        settings.show(Collections.singleton("held-out loss"), ALL_NAMES, XAxisChoice.STEP);
        assertEquals(Collections.singletonList(HELD_OUT), settings.shownSeries(ALL));
    }

    @Test
    public void aMetricSentForTheFirstTimeIsShown() {
        MetricChartSettings settings = new MetricChartSettings();
        settings.show(Collections.singleton("held-out loss"), Arrays.asList("held-out loss", "training loss"), XAxisChoice.STEP);
        assertTrue(settings.isShown("throughput"));
        assertFalse(settings.isShown("training loss"));
    }

    @Test
    public void stepsAreDrawnWhenEveryShownSeriesHasThem() {
        MetricChartSettings settings = new MetricChartSettings();
        settings.show(Arrays.asList("held-out loss", "training loss"), ALL_NAMES, XAxisChoice.STEP);
        assertEquals(XAxisChoice.STEP, settings.effectiveXAxis(ALL));
    }

    @Test
    public void aShownSeriesWithoutStepsFallsBackToTime() {
        MetricChartSettings settings = new MetricChartSettings();
        assertEquals(XAxisChoice.STEP, settings.getPreferredXAxis());
        assertEquals(XAxisChoice.TIME, settings.effectiveXAxis(ALL));
    }

    @Test
    public void hidingTheSeriesWithoutStepsGivesTheStepsBack() {
        MetricChartSettings settings = new MetricChartSettings();
        assertEquals(XAxisChoice.TIME, settings.effectiveXAxis(ALL));
        settings.show(Arrays.asList("held-out loss", "training loss"), ALL_NAMES, settings.getPreferredXAxis());
        assertEquals(XAxisChoice.STEP, settings.effectiveXAxis(ALL));
    }

    @Test
    public void timeAskedForIsTimeDrawn() {
        MetricChartSettings settings = new MetricChartSettings();
        settings.show(Collections.singleton("held-out loss"), ALL_NAMES, XAxisChoice.TIME);
        assertEquals(XAxisChoice.TIME, settings.effectiveXAxis(ALL));
    }

    @Test
    public void stepAvailabilityFollowsTheSelection() {
        assertTrue(MetricChartSettings.isStepAvailable(Arrays.asList(HELD_OUT, TRAINING)));
        assertFalse(MetricChartSettings.isStepAvailable(Arrays.asList(HELD_OUT, THROUGHPUT)));
        assertFalse(MetricChartSettings.isStepAvailable(Collections.<JordanMetricSeriesDTO>emptyList()));
    }

    @Test
    public void wholeSteps() {
        assertTrue(MetricChartSettings.hasWholeSteps(Arrays.asList(HELD_OUT, TRAINING, THROUGHPUT)));
        assertFalse(MetricChartSettings.hasWholeSteps(Collections.singletonList(series("loss", point(1, 2.5, 1000)))));
    }

    @Test
    public void xAgainstSteps() {
        assertEquals(1f, MetricChartSettings.xOf(point(0.5, 1.0, 1083), XAxisChoice.STEP, 990), 0f);
    }

    @Test
    public void xAgainstTimeIsCountedFromTheOrigin() {
        assertEquals(93f, MetricChartSettings.xOf(point(0.5, 1.0, 1083), XAxisChoice.TIME, 990), 0f);
    }

    @Test
    public void xAgainstTimeTellsSecondsApartOnARealTimestamp() {
        long origin = 1790000000L;
        float first = MetricChartSettings.xOf(point(0.5, null, origin + 1), XAxisChoice.TIME, origin);
        float second = MetricChartSettings.xOf(point(0.5, null, origin + 2), XAxisChoice.TIME, origin);
        assertEquals(1f, second - first, 0f);
    }

    @Test
    public void autoRefreshIsOnByDefault() {
        MetricChartSettings settings = new MetricChartSettings();
        assertTrue(settings.isAutoRefreshEnabled());
        settings.setAutoRefresh(false, 30);
        assertFalse(settings.isAutoRefreshEnabled());
        assertEquals(30, settings.getAutoRefreshPeriodSeconds());
    }
}
