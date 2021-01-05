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
public class JordanReadStatusDTO {
    private List<JordanStatusDTO> statuses;
}
