package com.mara.jordan.app.model.dto;

import com.google.common.collect.Lists;

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
    private List<JordanMessageStateAuditDTO> audit = Lists.newArrayList();
    private JordanParentTaskDTO parentTask;
    private JordanExecutedActionDTO action;
}