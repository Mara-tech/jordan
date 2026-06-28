package com.mara.jordan.app.adapter;

import android.content.Context;
import android.os.Build;

import com.mara.jordan.core.dto.JordanStatusDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatusFilterTypeAdapter extends ACheckBoxFilterAdapter<JordanStatusDTO> {

    private static final String TAG = "StatusFilterTypeAdapter";

    public StatusFilterTypeAdapter(Context ctx) {
        super(ctx);
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    protected List<String> prepareItems(JordanStatusDTO[] dtos) {
        // Stream.of(dtos).map(JordanStatusDTO::getType).distinct().sorted().collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        Set<String> uniqueValues = new HashSet<>();
        for (JordanStatusDTO statusDTO : dtos) {
            String type = statusDTO.getType();
            if (uniqueValues.add(type)) {
                list.add(type);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.sort(null);
        }
        return list;
    }
}