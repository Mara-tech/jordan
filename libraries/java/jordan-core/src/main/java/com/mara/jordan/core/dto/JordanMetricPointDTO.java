package com.mara.jordan.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanMetricPointDTO {
    /** The metric status this point comes from. */
    private long statusId;
    private double value;
    /** Null when the client sent the value without a progress point. */
    private Double step;
    /** Seconds since 1970/1/1. */
    private long timestamp;
}
