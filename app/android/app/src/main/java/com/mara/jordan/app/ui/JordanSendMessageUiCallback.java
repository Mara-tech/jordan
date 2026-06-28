package com.mara.jordan.app.ui;

import com.mara.jordan.app.api.JordanSendMessageCallback;
import com.mara.jordan.core.dto.JordanActionParameterDTO;

import java.util.List;

public interface JordanSendMessageUiCallback extends JordanSendMessageCallback {
    void alertMandatoryFieldMissing(List<JordanActionParameterDTO> missingInput);
}
