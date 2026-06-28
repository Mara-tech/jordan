package com.mara.jordan.core.dto;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanExecutedActionDTO {
    private String actionName;
    @Builder.Default
    private Map<String, Object> placeholders = new HashMap<>();

    @Override
    public String toString() {
        return actionName;
    }
}
