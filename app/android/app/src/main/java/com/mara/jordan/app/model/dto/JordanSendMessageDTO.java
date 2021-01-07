package com.mara.jordan.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JordanSendMessageDTO {
    private String author;
    private JordanSendMessageActionDTO action;
}
