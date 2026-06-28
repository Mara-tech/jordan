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
public class JordanActionParameterDTO {
    private String name;
    private String type;
    private boolean mandatory;
    private Object defaultValue;
}
