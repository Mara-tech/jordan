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
import com.mara.jordan.app.model.dummy.MockDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatusFilterTaskAdapter extends BaseAdapter {

    private static final String TAG = "StatusFilterTaskAdapter";
    private final List<String> tasks;
    private List<Boolean> taskChecked;
    private List<Boolean> tempView;
    private final Context context;
    private LayoutInflater inflater;


    public StatusFilterTaskAdapter(Context ctx) {
        this.context = ctx;

        tasks = MockDatabase.getTaskNames();

        //define retention/persistence policy
        taskChecked = Lists.newArrayList(
                true, true, true, true
        );

        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return tasks.size();
    }

    @Override
    public Object getItem(int position) {
        return tasks.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = inflater.inflate(R.layout.status_filter_dialog_item, parent, false);

        CheckBox checkBox = convertView.findViewById(R.id.status_filter_item_check);
        String task = tasks.get(position);
        boolean checked = taskChecked.get(position);
        checkBox.setText(task);
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
        taskChecked = Lists.newArrayList(tempView);
    }

    public void resetTempState() {
        tempView = Lists.newArrayList(taskChecked);
    }
    
    public Map<String, Boolean> getFilterMapping() {
        Map<String, Boolean> map = new HashMap<>();
        for (int i=0; i<tasks.size(); i++) {
            if (map.put(tasks.get(i), taskChecked.get(i)) != null) {
                Log.e(TAG, "Duplicate key in tasks " + tasks);
            }
        }
        return map;
    }
}