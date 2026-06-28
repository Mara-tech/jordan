package com.mara.jordan.app.model;

import android.content.Context;

import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanGetActionsCallback;
import com.mara.jordan.app.api.JordanReadMessagesCallback;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.api.JordanSendMessageCallback;
import com.mara.jordan.core.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.core.dto.JordanMessageStateDTO;
import com.mara.jordan.core.dto.JordanStatusDTO;

import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

/**
 * A client, as a root task, is also represented in {@link JordanTaskModel}.
 */
public class JordanTaskModel extends JordanClientModel implements JordanReadStatusCallback, JordanReadMessagesCallback, JordanGetActionsCallback {

    private static final String TAG = "JordanClientModel";
    private final long taskId;
    private final Map<Long, JordanTaskModel> subTaskModelInstances = new HashMap<>();
    private final JordanApi api;

    @Getter
    private JordanStatusDTO[] statuses = new JordanStatusDTO[]{};
    @Getter
    private JordanMessageStateDTO[] messages = new JordanMessageStateDTO[]{};
    @Getter
    private JordanActionDefinitionWithTaskDTO[] actionDefinitions;


    public JordanTaskModel(Context ctx, long taskId) {
        super(ctx);
        this.taskId = taskId;
        api = JordanApi.getInstance(ctx);
    }

    public void readStatus(int lineCount, JordanReadStatusCallback... callbacks) {
        api.readStatus(taskId, lineCount, ArrayUtils.add(callbacks, this));
    }

    @Override
    public void onStatusLoaded(JordanStatusDTO[] response) {
        statuses = response;
    }

    @Override
    public void onStatusLoadingError(String errorMessage) {

    }

    public void readMessages(JordanReadMessagesCallback... callbacks) {
        api.readMessages(taskId, ArrayUtils.add(callbacks, this));
    }

    @Override
    public void onMessagesLoaded(JordanMessageStateDTO[] response) {
        messages = response;
    }

    @Override
    public void onMessagesLoadingError(String errorMessage) {

    }

    public void readActionDefinitions(JordanGetActionsCallback... callbacks) {
        api.readActionDefinitions(taskId, ArrayUtils.add(callbacks, this));
    }

    @Override
    public void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions) {
        actionDefinitions = actions;
    }

    @Override
    public void onActionsLoadingError(String errorMessage) {

    }

    public void sendMessage(String actionName, Map<String, Object> placeholders, JordanSendMessageCallback... callbacks) {
        api.sendMessage(taskId, actionName, placeholders, callbacks);
    }

    public JordanTaskModel subTaskModel(long taskId) {
        JordanTaskModel subTaskModel = subTaskModelInstances.get(taskId);
        if(subTaskModel == null){
            subTaskModel = new JordanTaskModel(context, taskId);
            subTaskModelInstances.put(taskId, subTaskModel);
        }
        return subTaskModel;
    }
}
