package com.mara.jordan.core.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanMessageStateDTO {
    private long messageId;
    private String author;
    @Builder.Default
    private List<JordanMessageStateAuditDTO> audit = new ArrayList<>();
    private JordanParentTaskDTO parentTask;
    private JordanExecutedActionDTO action;
}
