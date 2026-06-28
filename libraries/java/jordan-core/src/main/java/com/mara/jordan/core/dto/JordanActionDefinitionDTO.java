package com.mara.jordan.core.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanActionDefinitionDTO {
    private String actionName;
    private List<JordanActionParameterDTO> parameters;
}
