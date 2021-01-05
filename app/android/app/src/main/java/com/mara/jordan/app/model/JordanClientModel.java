package com.mara.jordan.app.model;

import android.content.Context;

import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.model.dto.JordanStatusDTO;

import org.apache.commons.lang3.ArrayUtils;

public class JordanClientModel implements JordanModel, JordanReadStatusCallback {

    private static final String TAG = "JordanClientModel";
    private final long clientId;
    private final JordanApi api;
    private JordanStatusDTO[] statuses = new JordanStatusDTO[]{};

    public JordanClientModel(Context context, long clientId) {
        this.clientId = clientId;
        api = new JordanApi(context);
    }

    public void readStatus(JordanReadStatusCallback... callbacks) {
        api.readStatus(ArrayUtils.add(callbacks, this));
        //return applyFilters(textQuery, typeFilter, taskFilter, statuses);
    }

    @Override
    public void onStatusLoaded(JordanStatusDTO[] response) {
        statuses = response;
    }

    @Override
    public void onStatusLoadingError(String errorMessage) {

    }


    public JordanStatusDTO[] getStatuses() {
        return statuses;
    }
}
