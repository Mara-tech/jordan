package com.mara.jordan.core.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Values sent under one metric name by one task, as returned by {@code GET /admin/{taskId}/metrics}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanMetricSeriesDTO {
    private String name;
    private JordanParentTaskDTO parentTask;
    /** In the order the server received them. */
    private List<JordanMetricPointDTO> points;
}
