package com.mara.jordan.core.dto;

import java.util.List;

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
public class JordanActionDefinitionWithTaskDTO {
    private String actionName;
    private List<JordanActionParameterDTO> parameters;
    private JordanParentTaskDTO parentTask;
}
