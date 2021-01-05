package com.mara.jordan.app.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanTaskDTO {
    private long taskId;
    private String name;
    private String state;
    private List<JordanActionDefinitionDTO> actions;
}
