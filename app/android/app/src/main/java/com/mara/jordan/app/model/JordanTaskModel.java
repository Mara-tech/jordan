package com.mara.jordan.app.model;

import android.content.Context;

import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanGetActionsCallback;
import com.mara.jordan.app.api.JordanReadMessagesCallback;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.api.JordanSendMessageCallback;
import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.model.dto.JordanStatusDTO;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Map;

import lombok.Getter;

public class JordanTaskModel implements JordanModel, JordanReadStatusCallback, JordanReadMessagesCallback, JordanGetActionsCallback {

    private static final String TAG = "JordanClientModel";
    private final long clientId;
    private final JordanApi api;

    @Getter
    private JordanStatusDTO[] statuses = new JordanStatusDTO[]{};
    @Getter
    private JordanMessageStateDTO[] messages = new JordanMessageStateDTO[]{};
    @Getter
    private JordanActionDefinitionWithTaskDTO[] actionDefinitions;

    public JordanTaskModel(Context context, long clientId) {
        super();
        this.clientId = clientId;
        api = new JordanApi(context);
    }

    public void readStatus(JordanReadStatusCallback... callbacks) {
        api.readStatus(ArrayUtils.add(callbacks, this));
    }

    @Override
    public void onStatusLoaded(JordanStatusDTO[] response) {
        statuses = response;
    }

    @Override
    public void onStatusLoadingError(String errorMessage) {

    }

    public void readMessages(JordanReadMessagesCallback... callbacks) {
        api.readMessages(ArrayUtils.add(callbacks, this));
    }

    @Override
    public void onMessagesLoaded(JordanMessageStateDTO[] response) {
        messages = response;
    }

    @Override
    public void onMessagesLoadingError(String errorMessage) {

    }

    public void readActionDefinitions(JordanGetActionsCallback... callbacks) {
        api.readActionDefinitions(ArrayUtils.add(callbacks, this));
    }

    @Override
    public void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions) {
        actionDefinitions = actions;
    }

    @Override
    public void onActionsLoadingError(String errorMessage) {

    }

    public void sendMessage(long taskId, String actionName, Map<String, Object> placeholders, JordanSendMessageCallback... callbacks) {
        api.sendMessage(taskId, actionName, placeholders, callbacks);
    }
}
