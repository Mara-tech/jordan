package com.mara.jordan.app.adapter;

import android.content.Context;
import android.os.Build;

import com.mara.jordan.app.model.dto.JordanMessageStateDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MessageFilterAuthorAdapter extends ACheckBoxFilterAdapter<JordanMessageStateDTO> {

    private static final String TAG = "MessageFilterAuthorAdapter";

    public MessageFilterAuthorAdapter(Context ctx) {
        super(ctx);
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    protected List<String> prepareItems(JordanMessageStateDTO[] dtos) {
//        return Stream.of(dtos).map(JordanMessageStateDTO::getAuthor).distinct().sorted().collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        Set<String> uniqueValues = new HashSet<>();
        for (JordanMessageStateDTO dto : dtos) {
            String author = dto.getAuthor();
            if (uniqueValues.add(author)) {
                list.add(author);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.sort(null);
        }
        return list;
    }
}
