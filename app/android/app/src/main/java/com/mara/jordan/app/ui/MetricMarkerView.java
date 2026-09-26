package com.mara.jordan.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.mara.jordan.app.R;
import com.mara.jordan.core.DateUtils;
import com.mara.jordan.core.MetricUtils;
import com.mara.jordan.core.dto.JordanMetricPointDTO;

/**
 * Shown over the point the operator taps: which curve, the exact value, its step and its time.
 */
@SuppressLint("ViewConstructor")
public class MetricMarkerView extends MarkerView {

    /**
     * What a chart entry carries, for this view to describe it.
     */
    public static class PointInfo {
        final String label;
        final JordanMetricPointDTO point;

        public PointInfo(String label, JordanMetricPointDTO point) {
            this.label = label;
            this.point = point;
        }
    }

    private final TextView text;

    public MetricMarkerView(Context context) {
        super(context, R.layout.metric_marker_view);
        text = findViewById(R.id.metric_marker_text);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e.getData() instanceof PointInfo) {
            PointInfo info = (PointInfo) e.getData();
            String when = DateUtils.formatTimestamp(info.point.getTimestamp(), false);
            String where = info.point.getStep() == null
                    ? when
                    : getContext().getString(R.string.metric_marker_step, MetricUtils.formatNumber(info.point.getStep()), when);
            text.setText(getContext().getString(R.string.metric_marker, info.label, MetricUtils.formatNumber(info.point.getValue()), where));
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // centred above the point
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}
