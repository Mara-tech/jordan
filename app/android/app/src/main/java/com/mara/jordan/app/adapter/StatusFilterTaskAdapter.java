package com.mara.jordan.app.adapter;

import android.content.Context;
import android.os.Build;

import com.mara.jordan.core.dto.JordanParentTaskDTO;
import com.mara.jordan.core.dto.JordanStatusDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatusFilterTaskAdapter extends ACheckBoxFilterAdapter<JordanStatusDTO> {

    private static final String TAG = "StatusFilterTaskAdapter";

    public StatusFilterTaskAdapter(Context ctx) {
        super(ctx);
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    protected List<String> prepareItems(JordanStatusDTO[] dtos) {
        // Stream.of(dtos).map(JordanStatusDTO::getParentTask).map(JordanParentTaskDTO::getName).distinct().sorted().collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        Set<String> uniqueValues = new HashSet<>();
        for (JordanStatusDTO status : dtos) {
            JordanParentTaskDTO parentTask = status.getParentTask();
            String name = parentTask.getName();
            if (uniqueValues.add(name)) {
                list.add(name);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.sort(null);
        }
        return list;
    }
}