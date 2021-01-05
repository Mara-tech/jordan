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
import com.mara.jordan.app.model.dto.JordanParentTaskDTO;
import com.mara.jordan.app.model.dto.JordanStatusDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatusFilterTaskAdapter extends ArrayAdapter<String> {

    private static final String TAG = "StatusFilterTaskAdapter";
    private List<String> tasks;
    private List<Boolean> taskChecked;
    private List<Boolean> tempView;
    private LayoutInflater mInflater;


    public StatusFilterTaskAdapter(Context ctx) {
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

        return view;
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

    public void onStatusLoaded(JordanStatusDTO[] statuses) {
        tasks = extractDistinctTasks(statuses);
        taskChecked = initCheckedTasks();
        resetTempState();
        clear();
        addAll(tasks);
    }

    private List<Boolean> initCheckedTasks() {
        //tasks.stream().map(this::taskToCheckedInitState).collect(Collectors.toList());
        List<Boolean> checked = new ArrayList<>();
        for (String task : tasks) {
            Boolean aBoolean = taskToCheckedInitState(task);
            checked.add(aBoolean);
        }
        return checked;
    }

    private Boolean taskToCheckedInitState(String task) {
        //TODO define retention/persistence policy
        return true;
    }

    private List<String> extractDistinctTasks(JordanStatusDTO[] statuses) {
        // Stream.of(statuses).map(JordanStatusDTO::getParentTask).map(JordanParentTaskDTO::getName).distinct().sorted().collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        Set<String> uniqueValues = new HashSet<>();
        for (JordanStatusDTO status : statuses) {
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