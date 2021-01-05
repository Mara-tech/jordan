package com.mara.jordan.app.adapter;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.google.common.collect.Lists;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dto.JordanStatusDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatusFilterTypeAdapter extends ArrayAdapter<String> {

    private static final String TAG = "StatusFilterTypeAdapter";
    private List<String> types = Lists.newArrayList();
    private List<Boolean> typeChecked;
    private List<Boolean> tempView;
    private LayoutInflater mInflater;


    public StatusFilterTypeAdapter(Context ctx) {
        super(ctx, 0);
        mInflater = LayoutInflater.from(ctx);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.status_filter_dialog_item, parent, false);

        CheckBox checkBox = view.findViewById(R.id.status_filter_item_check);
        String type = types.get(position);
        boolean checked = typeChecked.get(position);
        checkBox.setText(type);
        checkBox.setChecked(checked);
        tempView.set(position, checked);

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                tempView.set(position, isChecked);
            }
        });

        return view;
    }

    public void applyTempView() {
        typeChecked = Lists.newArrayList(tempView);
    }

    public void resetTempState() {
        tempView = Lists.newArrayList(typeChecked);
    }

    public Map<String, Boolean> getFilterMapping() {
        Map<String, Boolean> map = new HashMap<>();
        for (int i=0; i<types.size(); i++) {
            if (map.put(types.get(i), typeChecked.get(i)) != null) {
                Log.e(TAG, "Duplicate key in types " + types);
            }
        }
        return map;
    }

    public void onStatusLoaded(JordanStatusDTO[] statuses) {
        types = extractDistinctTypes(statuses);
        typeChecked = initCheckedTypes();
        resetTempState();
        clear();
        addAll(types);
    }

    private List<Boolean> initCheckedTypes() {
        //types.stream().map(this::typeToCheckedInitState).collect(Collectors.toList());
        List<Boolean> checked = new ArrayList<>();
        for (String type : types) {
            Boolean aBoolean = typeToCheckedInitState(type);
            checked.add(aBoolean);
        }
        return checked;
    }

    private Boolean typeToCheckedInitState(String type) {
        //TODO define retention/persistence policy
        return true;
    }

    private List<String> extractDistinctTypes(JordanStatusDTO[] statuses) {
        // Stream.of(statuses).map(JordanStatusDTO::getType).distinct().sorted().collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        Set<String> uniqueValues = new HashSet<>();
        for (JordanStatusDTO statusDTO : statuses) {
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