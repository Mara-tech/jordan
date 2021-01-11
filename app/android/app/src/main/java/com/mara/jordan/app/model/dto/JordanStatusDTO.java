package com.mara.jordan.app.model.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanStatusDTO {
    private long statusId;
    private String type;
    private String status;
    private long timestamp;
    private JordanParentTaskDTO parentTask;
}
