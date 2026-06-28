package com.mara.jordan.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class JordanParentTaskDTO {
    private long taskId;
    private String name;
    private Integer progress;
    private String state;
}
