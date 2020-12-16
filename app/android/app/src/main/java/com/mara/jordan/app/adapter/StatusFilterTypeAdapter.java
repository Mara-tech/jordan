package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.google.common.collect.Lists;
import com.mara.jordan.app.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatusFilterTypeAdapter extends BaseAdapter {

    private static final String TAG = "StatusFilterTypeAdapter";
    private final List<String> types;
    private List<Boolean> typeChecked;
    private List<Boolean> tempView;
    private final Context context;
    private LayoutInflater inflater;


    public StatusFilterTypeAdapter(Context ctx) {
        this.context = ctx;

        //should not be static list. type is free (beyond suggested/standard types from lib)
        types = Lists.newArrayList(
                ReadStatusAdapter.STATUS_TYPE_SUCCESS,
                ReadStatusAdapter.STATUS_TYPE_FAILURE,
                ReadStatusAdapter.STATUS_TYPE_GENERAL,
                ReadStatusAdapter.STATUS_TYPE_PROGRESS
        );

        //define retention/persistence policy
        typeChecked = Lists.newArrayList(
                true, true, true, true
        );

        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return types.size();
    }

    @Override
    public Object getItem(int position) {
        return types.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = inflater.inflate(R.layout.status_filter_dialog_item, parent, false);

        CheckBox checkBox = convertView.findViewById(R.id.status_filter_item_check);
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

        return convertView;
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
}