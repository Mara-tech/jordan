package com.mara.jordan.app.adapter;

import android.content.Context;
import android.os.Build;

import com.mara.jordan.core.dto.JordanMessageStateAuditDTO;
import com.mara.jordan.core.dto.JordanMessageStateDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mara.jordan.core.JordanHelper.getCurrentState;

public class MessageFilterStateAdapter extends ACheckBoxFilterAdapter<JordanMessageStateDTO> {

    private static final String TAG = "MessageFilterStateAdapter";

    public MessageFilterStateAdapter(Context ctx) {
        super(ctx);
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    protected List<String> prepareItems(JordanMessageStateDTO[] dtos) {
//        return Stream.of(dtos).map(JordanMessageStateDTO::getAudit).map(MessagesStateAdapter::getCurrentState).distinct().sorted().collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        Set<String> uniqueValues = new HashSet<>();
        for (JordanMessageStateDTO dto : dtos) {
            List<JordanMessageStateAuditDTO> audit = dto.getAudit();
            String currentState = getCurrentState(audit);
            if (uniqueValues.add(currentState)) {
                list.add(currentState);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.sort(null);
        }
        return list;
    }
}
