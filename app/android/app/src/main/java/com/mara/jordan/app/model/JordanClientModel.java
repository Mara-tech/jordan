package com.mara.jordan.app.model;

import android.content.Context;

import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanReadMessagesCallback;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.model.dto.JordanStatusDTO;

import org.apache.commons.lang3.ArrayUtils;

import lombok.Getter;

public class JordanClientModel implements JordanModel, JordanReadStatusCallback, JordanReadMessagesCallback {

    private static final String TAG = "JordanClientModel";
    private final long clientId;
    private final JordanApi api;

    @Getter
    private JordanStatusDTO[] statuses = new JordanStatusDTO[]{};
    @Getter
    private JordanMessageStateDTO[] messages = new JordanMessageStateDTO[]{};

    public JordanClientModel(Context context, long clientId) {
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
}
