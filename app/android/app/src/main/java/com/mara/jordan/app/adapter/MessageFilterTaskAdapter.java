package com.mara.jordan.app.adapter;

import android.content.Context;
import android.os.Build;

import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.model.dto.JordanParentTaskDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MessageFilterTaskAdapter extends ACheckBoxFilterAdapter<JordanMessageStateDTO> {

    private static final String TAG = "MessageFilterTaskAdapter";

    public MessageFilterTaskAdapter(Context ctx) {
        super(ctx);
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    protected List<String> prepareItems(JordanMessageStateDTO[] dtos) {
//         return Stream.of(dtos).map(JordanMessageStateDTO::getParentTask).map(JordanParentTaskDTO::getName).distinct().sorted().collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        Set<String> uniqueValues = new HashSet<>();
        for (JordanMessageStateDTO dto : dtos) {
            JordanParentTaskDTO parentTask = dto.getParentTask();
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
