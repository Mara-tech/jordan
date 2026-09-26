package com.mara.jordan.app.api;

import com.mara.jordan.core.dto.JordanMetricSeriesDTO;

public interface JordanReadMetricsCallback {
    void onMetricsLoaded(JordanMetricSeriesDTO[] series);
    void onMetricsLoadingError(String errorMessage);
}
