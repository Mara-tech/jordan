package com.mara.jordan.app.model;

import com.mara.jordan.core.MetricUtils;
import com.mara.jordan.core.dto.JordanMetricPointDTO;
import com.mara.jordan.core.dto.JordanMetricSeriesDTO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the metrics chart of a task shows, and against what. Kept by {@link JordanTaskModel}, so it
 * outlives the tab: coming back to the chart finds it as it was left.
 */
public class MetricChartSettings {

    public enum XAxisChoice { STEP, TIME }

    public static final boolean DEFAULT_AUTO_REFRESH = true;
    public static final int DEFAULT_AUTO_REFRESH_PERIOD_SECONDS = 10;

    /**
     * Names unchecked by the operator. Stored rather than the checked ones, so that a metric sent for the
     * first time is shown: a curve that appears while the task runs is not hidden by default.
     */
    private final Set<String> hiddenNames = new HashSet<>();

    /**
     * The axis the operator asked for. Steps are drawn only when every shown series can be, so a series
     * without steps, once hidden again, gives the steps back.
     */
    private XAxisChoice preferredXAxis = XAxisChoice.STEP;

    private boolean autoRefreshEnabled = DEFAULT_AUTO_REFRESH;
    private int autoRefreshPeriodSeconds = DEFAULT_AUTO_REFRESH_PERIOD_SECONDS;

    public boolean isShown(String name) {
        return !hiddenNames.contains(name);
    }

    public List<JordanMetricSeriesDTO> shownSeries(List<JordanMetricSeriesDTO> all) {
        List<JordanMetricSeriesDTO> shown = new ArrayList<>();
        for (JordanMetricSeriesDTO s : all) {
            if (isShown(s.getName())) {
                shown.add(s);
            }
        }
        return shown;
    }

    /**
     * @param shownNames the names checked, among {@code allNames}
     */
    public void show(Collection<String> shownNames, Collection<String> allNames, XAxisChoice preferredXAxis) {
        for (String name : allNames) {
            if (shownNames.contains(name)) {
                hiddenNames.remove(name);
            } else {
                hiddenNames.add(name);
            }
        }
        this.preferredXAxis = preferredXAxis;
    }

    public XAxisChoice getPreferredXAxis() {
        return preferredXAxis;
    }

    /**
     * The axis the chart is drawn against: steps when asked for and every shown point has one, time otherwise.
     */
    public XAxisChoice effectiveXAxis(List<JordanMetricSeriesDTO> all) {
        return preferredXAxis == XAxisChoice.STEP && isStepAvailable(shownSeries(all))
                ? XAxisChoice.STEP
                : XAxisChoice.TIME;
    }

    /**
     * Whether these series can be drawn against steps — what enables the choice in the settings.
     */
    public static boolean isStepAvailable(List<JordanMetricSeriesDTO> series) {
        return MetricUtils.canPlotAgainstStep(series);
    }

    /**
     * Whether every step of these series is a whole number, so the axis can be graduated one step at a time.
     */
    public static boolean hasWholeSteps(List<JordanMetricSeriesDTO> series) {
        for (JordanMetricSeriesDTO s : series) {
            if (s.getPoints() == null) {
                continue;
            }
            for (JordanMetricPointDTO p : s.getPoints()) {
                if (p.getStep() != null && p.getStep() != Math.rint(p.getStep())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Position of a point along the chosen axis. Time is counted in seconds from {@code timeOrigin}: a chart
     * holds its positions as floats, which cannot tell apart two timestamps of the same two minutes
     * (a float carries 24 bits, a timestamp 31), while an offset stays exact for months.
     */
    public static float xOf(JordanMetricPointDTO point, XAxisChoice axis, long timeOrigin) {
        if (axis == XAxisChoice.STEP) {
            return point.getStep().floatValue();
        }
        return (float) (point.getTimestamp() - timeOrigin);
    }

    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }

    public int getAutoRefreshPeriodSeconds() {
        return autoRefreshPeriodSeconds;
    }

    public void setAutoRefresh(boolean enabled, int periodSeconds) {
        this.autoRefreshEnabled = enabled;
        this.autoRefreshPeriodSeconds = periodSeconds;
    }
}
