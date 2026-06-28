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
public class JordanTaskDTO {
    private long taskId;
    private String name;
    private Integer progress;
    private String state;
    private List<JordanActionDefinitionDTO> actions;
    private List<JordanTaskDTO> tasks;
}
