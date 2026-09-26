package com.mara.jordan.core;

import com.mara.jordan.core.dto.JordanMetricPointDTO;
import com.mara.jordan.core.dto.JordanMetricSeriesDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reading metric series: how a value reads, which series are shown, and against what they can be drawn.
 */
public final class MetricUtils {

    /**
     * How a metric reads as a log line, e.g. {@code held-out loss = 0.6648 (step 3)}.
     */
    public static String describe(String name, double value, Double step) {
        String text = name + " = " + formatNumber(value);
        return step == null ? text : text + " (step " + formatNumber(step) + ")";
    }

    /**
     * A number without the noise of its binary form: {@code 3.0} reads {@code 3}, {@code 1e-7} reads
     * {@code 0.0000001}.
     */
    public static String formatNumber(double number) {
        if (number == 0) {
            return "0"; // stripTrailingZeros leaves "0.0" alone on some JDKs, and -0.0 is still 0
        }
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return String.valueOf(number);
        }
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    /**
     * Distinct metric names, in the order their first series comes.
     */
    public static List<String> names(Collection<JordanMetricSeriesDTO> series) {
        Set<String> names = new LinkedHashSet<>();
        for (JordanMetricSeriesDTO s : series) {
            names.add(s.getName());
        }
        return new ArrayList<>(names);
    }

    /**
     * The series carrying one of the given names — every task's, when several tasks share a name.
     */
    public static List<JordanMetricSeriesDTO> select(Collection<JordanMetricSeriesDTO> series, Collection<String> names) {
        List<JordanMetricSeriesDTO> selected = new ArrayList<>();
        for (JordanMetricSeriesDTO s : series) {
            if (names.contains(s.getName())) {
                selected.add(s);
            }
        }
        return selected;
    }

    /**
     * Whether these series can be drawn against steps: every point of every one of them has a step.
     * A single point without one would have no place on that axis. False when there is nothing to draw.
     */
    public static boolean canPlotAgainstStep(Collection<JordanMetricSeriesDTO> series) {
        boolean anyPoint = false;
        for (JordanMetricSeriesDTO s : series) {
            for (JordanMetricPointDTO p : points(s)) {
                if (p.getStep() == null) {
                    return false;
                }
                anyPoint = true;
            }
        }
        return anyPoint;
    }

    /**
     * Earliest timestamp among the points of these series, in seconds: the origin of a time axis.
     * 0 when there is no point.
     */
    public static long timeOrigin(Collection<JordanMetricSeriesDTO> series) {
        long origin = Long.MAX_VALUE;
        for (JordanMetricSeriesDTO s : series) {
            for (JordanMetricPointDTO p : points(s)) {
                origin = Math.min(origin, p.getTimestamp());
            }
        }
        return origin == Long.MAX_VALUE ? 0L : origin;
    }

    /**
     * Label of a series among others: its name, followed by its task's when another task sends a
     * series under the same name — two curves called « loss » could not be told apart otherwise.
     */
    public static String label(JordanMetricSeriesDTO series, Collection<JordanMetricSeriesDTO> among) {
        Map<String, Set<Long>> tasksByName = new HashMap<>();
        for (JordanMetricSeriesDTO s : among) {
            Set<Long> tasks = tasksByName.get(s.getName());
            if (tasks == null) {
                tasks = new HashSet<>();
                tasksByName.put(s.getName(), tasks);
            }
            tasks.add(s.getParentTask() == null ? null : s.getParentTask().getTaskId());
        }
        Set<Long> tasks = tasksByName.get(series.getName());
        if (tasks == null || tasks.size() < 2 || series.getParentTask() == null) {
            return series.getName();
        }
        return series.getName() + " [" + series.getParentTask().getName() + "]";
    }

    private static List<JordanMetricPointDTO> points(JordanMetricSeriesDTO series) {
        return series.getPoints() == null ? new ArrayList<JordanMetricPointDTO>() : series.getPoints();
    }

    private MetricUtils() {}
}
