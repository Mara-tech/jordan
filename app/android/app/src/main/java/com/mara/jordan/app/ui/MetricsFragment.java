package com.mara.jordan.app.ui;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanReadMetricsCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.app.model.MetricChartSettings;
import com.mara.jordan.app.model.MetricChartSettings.XAxisChoice;
import com.mara.jordan.core.DateUtils;
import com.mara.jordan.core.MetricUtils;
import com.mara.jordan.core.dto.JordanMetricPointDTO;
import com.mara.jordan.core.dto.JordanMetricSeriesDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values a task sends as metric statuses, drawn as curves: one per metric name and task.
 * Which names are drawn, and against steps or time, is chosen in the settings dialog.
 */
public class MetricsFragment extends Fragment implements JordanReadMetricsCallback, JordanRefreshable {

    public static final String TAG = "METRICS_FRAG";
    /**
     * Displayed elements in the NumberPicker, in seconds.
     */
    private static final String[] PERIOD_CHOICES = {"1", "5", "10", "30", "60", "300"};
    /**
     * Beyond this many points, a curve is drawn as a line only: circles would hide it.
     */
    private static final int MAX_POINTS_WITH_CIRCLES = 60;
    private static final Comparator<Entry> BY_X = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            return Float.compare(a.getX(), b.getX());
        }
    };

    static {
        //Ensure choices are valid integers
        for (String p : PERIOD_CHOICES) {
            Integer.parseInt(p);
        }
    }

    private JordanTaskModel model;
    private LineChart chart;
    private ProgressBar loading;
    private int[] seriesColors;

    private final Handler autoRefreshScheduler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public MetricsFragment() {
    }

    public static MetricsFragment newInstance() {
        return new MetricsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = ClientInteractionsFragment.taskModelOf(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.metrics_view, container, false);
        setHasOptionsMenu(true);
        loading = view.findViewById(R.id.metrics_loading);
        chart = view.findViewById(R.id.metrics_chart);
        seriesColors = readSeriesColors();
        setupChart();
        return view;
    }

    private int[] readSeriesColors() {
        TypedArray colors = getResources().obtainTypedArray(R.array.metric_series_colors);
        int[] result = new int[colors.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = colors.getColor(i, ContextCompat.getColor(requireContext(), R.color.status_type_default));
        }
        colors.recycle();
        return result;
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setNoDataText(getString(R.string.metrics_loading)); // until the first answer
        chart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.dark_grey));
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setAvoidFirstLastClipping(true);
        chart.getXAxis().setLabelCount(4);
        Legend legend = chart.getLegend();
        legend.setWordWrapEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        MetricMarkerView marker = new MetricMarkerView(requireContext());
        marker.setChartView(chart);
        chart.setMarker(marker);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (model.getMetricSeries().length > 0) {
            // what the model read last, while a fresh read is on its way
            drawChart(Arrays.asList(model.getMetricSeries()), true);
        }
        refreshMetrics();
    }

    @Override
    public void onStop() {
        super.onStop();
        cancelScheduledRefresh();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.metrics_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.refresh_metrics) {
            refreshMetrics();
            return true;
        } else if (itemId == R.id.metrics_settings) {
            displaySettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshMetrics() {
        loading.setVisibility(View.VISIBLE);
        model.readMetrics(this);
    }

    @Override
    public void refreshContent() {
        refreshMetrics();
    }

    @Override
    public void onMetricsLoaded(JordanMetricSeriesDTO[] series) {
        if (getView() == null) {
            return; // the answer of a tab the operator already left
        }
        loading.setVisibility(View.GONE);
        drawChart(Arrays.asList(series), false);
        Log.i(TAG, "metrics loaded success");
        setupAutoRefresh();
    }

    @Override
    public void onMetricsLoadingError(String errorMessage) {
        if (getView() == null) {
            return;
        }
        loading.setVisibility(View.GONE);
        if (getContext() != null) {
            Snackbar.make(getView(), R.string.metrics_refresh_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.metrics_refresh_failure_details, v -> new MaterialAlertDialogBuilder(getContext())
                            .setTitle(R.string.metrics_refresh_failure_details_dialog)
                            .setItems(new String[]{errorMessage}, null)
                            .show())
                    .show();
        }
        Log.i(TAG, "metrics loaded failure");
        setupAutoRefresh();
    }

    /**
     * @param resetViewport true when what is drawn changed shape (other curves, other axis): the zoom the
     *                      operator had no longer applies. A mere refresh keeps it.
     */
    private void drawChart(List<JordanMetricSeriesDTO> all, boolean resetViewport) {
        MetricChartSettings settings = model.getMetricChartSettings();
        List<JordanMetricSeriesDTO> shown = settings.shownSeries(all);
        if (shown.isEmpty()) {
            chart.setNoDataText(getString(all.isEmpty() ? R.string.no_metric_to_display : R.string.no_metric_shown));
            chart.clear(); // also redraws, with the text above
            return;
        }

        XAxisChoice axis = settings.effectiveXAxis(all);
        long timeOrigin = MetricUtils.timeOrigin(shown);
        List<ILineDataSet> dataSets = new ArrayList<>();
        for (JordanMetricSeriesDTO series : shown) {
            String label = MetricUtils.label(series, shown);
            List<Entry> entries = new ArrayList<>();
            for (JordanMetricPointDTO point : series.getPoints()) {
                entries.add(new Entry(MetricChartSettings.xOf(point, axis, timeOrigin), (float) point.getValue(),
                        new MetricMarkerView.PointInfo(label, point)));
            }
            // a chart looks points up by position: they have to come in order along the axis
            Collections.sort(entries, BY_X);
            dataSets.add(makeDataSet(entries, label, colorOf(series, all)));
        }

        XAxis xAxis = chart.getXAxis();
        if (axis == XAxisChoice.STEP) {
            xAxis.setValueFormatter((ValueFormatter) null); // null restores the default number formatter
            xAxis.setGranularity(1f);
            xAxis.setGranularityEnabled(MetricChartSettings.hasWholeSteps(shown));
        } else {
            xAxis.setValueFormatter(new TimeAxisFormatter(timeOrigin));
            xAxis.setGranularity(1f); // a second : timestamps hold nothing finer
            xAxis.setGranularityEnabled(true);
        }
        chart.setData(new LineData(dataSets));
        if (resetViewport) {
            chart.fitScreen();
        }
        chart.invalidate();
    }

    private LineDataSet makeDataSet(List<Entry> entries, String label, @ColorInt int color) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawCircles(entries.size() <= MAX_POINTS_WITH_CIRCLES);
        dataSet.setDrawValues(false);
        dataSet.setHighLightColor(ContextCompat.getColor(requireContext(), R.color.dark_grey));
        return dataSet;
    }

    /**
     * Picked by the rank of the series among all of them, not among the shown ones: a curve keeps its
     * colour when another is hidden.
     */
    @ColorInt
    private int colorOf(JordanMetricSeriesDTO series, List<JordanMetricSeriesDTO> all) {
        int rank = Math.max(0, all.indexOf(series));
        return seriesColors[rank % seriesColors.length];
    }

    /**
     * Labels a time axis whose positions are seconds counted from {@code timeOrigin}.
     */
    private static class TimeAxisFormatter extends ValueFormatter {
        private final long timeOrigin;

        TimeAxisFormatter(long timeOrigin) {
            this.timeOrigin = timeOrigin;
        }

        @Override
        public String getFormattedValue(float value) {
            return DateUtils.formatTimestamp(timeOrigin + Math.round(value), false);
        }
    }

    /**
     * Metrics to draw (a checkbox per name), axis (steps, disabled while a checked series has a point
     * without one, or time), auto-refresh.
     */
    private void displaySettings() {
        final List<JordanMetricSeriesDTO> all = Arrays.asList(model.getMetricSeries());
        final List<String> names = MetricUtils.names(all);
        final MetricChartSettings settings = model.getMetricChartSettings();

        View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.metrics_settings_dialog, null);
        LinearLayout namesContainer = dialogView.findViewById(R.id.metrics_settings_names);
        TextView noName = dialogView.findViewById(R.id.metrics_settings_no_name);
        RadioGroup axisGroup = dialogView.findViewById(R.id.metrics_settings_axis);
        RadioButton stepButton = dialogView.findViewById(R.id.metrics_settings_axis_step);
        TextView stepUnavailable = dialogView.findViewById(R.id.metrics_settings_axis_step_unavailable);
        CheckBox autoRefresh = dialogView.findViewById(R.id.metrics_settings_auto_refresh_cb);
        NumberPicker periodPicker = dialogView.findViewById(R.id.metrics_settings_auto_refresh_period);

        final Map<String, CheckBox> boxes = new LinkedHashMap<>();
        for (String name : names) {
            View item = getLayoutInflater().inflate(R.layout.filter_dialog_item, namesContainer, false);
            CheckBox box = item.findViewById(R.id.filter_item_check);
            box.setText(name);
            box.setChecked(settings.isShown(name));
            namesContainer.addView(item);
            boxes.put(name, box);
        }
        noName.setVisibility(names.isEmpty() ? View.VISIBLE : View.GONE);

        // the axis asked for, kept apart from the one checked: unchecking the series that took the
        // steps away gives them back
        final XAxisChoice[] preferred = {settings.getPreferredXAxis()};
        final boolean[] updatingAxis = {false};
        final Runnable updateAxis = () -> {
            boolean stepAvailable = MetricChartSettings.isStepAvailable(MetricUtils.select(all, checkedNames(boxes)));
            updatingAxis[0] = true;
            stepButton.setEnabled(stepAvailable);
            axisGroup.check(stepAvailable && preferred[0] == XAxisChoice.STEP
                    ? R.id.metrics_settings_axis_step
                    : R.id.metrics_settings_axis_time);
            stepUnavailable.setVisibility(stepAvailable ? View.GONE : View.VISIBLE);
            updatingAxis[0] = false;
        };
        axisGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!updatingAxis[0]) {
                preferred[0] = checkedId == R.id.metrics_settings_axis_step ? XAxisChoice.STEP : XAxisChoice.TIME;
            }
        });
        for (CheckBox box : boxes.values()) {
            box.setOnCheckedChangeListener((buttonView, isChecked) -> updateAxis.run());
        }
        updateAxis.run();

        autoRefresh.setChecked(settings.isAutoRefreshEnabled());
        periodPicker.setMinValue(0);
        periodPicker.setMaxValue(PERIOD_CHOICES.length - 1);
        periodPicker.setDisplayedValues(PERIOD_CHOICES);
        periodPicker.setValue(periodIndex(settings.getAutoRefreshPeriodSeconds()));
        periodPicker.setEnabled(autoRefresh.isChecked());
        autoRefresh.setOnCheckedChangeListener((buttonView, isChecked) -> periodPicker.setEnabled(isChecked));

        new AlertDialog.Builder(requireActivity())
                .setView(dialogView)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    settings.show(checkedNames(boxes), names, preferred[0]);
                    settings.setAutoRefresh(autoRefresh.isChecked(), Integer.parseInt(PERIOD_CHOICES[periodPicker.getValue()]));
                    drawChart(all, true);
                    setupAutoRefresh();
                })
                .setNeutralButton(R.string.cancel, (dialog, which) -> {})
                .show();
    }

    private static List<String> checkedNames(Map<String, CheckBox> boxes) {
        List<String> checked = new ArrayList<>();
        for (Map.Entry<String, CheckBox> box : boxes.entrySet()) {
            if (box.getValue().isChecked()) {
                checked.add(box.getKey());
            }
        }
        return checked;
    }

    private static int periodIndex(int seconds) {
        for (int i = 0; i < PERIOD_CHOICES.length; i++) {
            if (Integer.parseInt(PERIOD_CHOICES[i]) == seconds) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Decides whether next (auto-)refresh should be scheduled, or not.
     */
    private void setupAutoRefresh() {
        cancelScheduledRefresh();
        if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED
                && model.getMetricChartSettings().isAutoRefreshEnabled()) {
            autoRefreshRunnable = this::refreshMetrics;
            int period = model.getMetricChartSettings().getAutoRefreshPeriodSeconds();
            autoRefreshScheduler.postDelayed(autoRefreshRunnable, 1000L * period);
            Log.d(TAG, "Refresh scheduled in " + period + "sec");
        }
    }

    private void cancelScheduledRefresh() {
        if (autoRefreshRunnable != null) {
            autoRefreshScheduler.removeCallbacks(autoRefreshRunnable);
            autoRefreshRunnable = null;
        }
    }
}
