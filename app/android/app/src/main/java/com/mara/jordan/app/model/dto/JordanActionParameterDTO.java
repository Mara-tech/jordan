package com.mara.jordan.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanActionParameterDTO {
    private String name;
    private String type;
    private boolean mandatory;
    private Object defaultValue;
}
