package com.mara.jordan.core.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanSendMessageActionDTO {
    private String actionName;
    private Map<String, Object> placeholders;
}
