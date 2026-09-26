package com.mara.jordan.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Named value carried by a {@code metric} status: one curve per name.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanMetricDTO {
    private String name;
    private double value;
    /** Progress point the value belongs to (an epoch, an iteration); null places the value in time. */
    private Double step;
}
