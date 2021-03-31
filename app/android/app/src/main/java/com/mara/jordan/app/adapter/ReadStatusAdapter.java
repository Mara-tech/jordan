package com.mara.jordan.app.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.app.model.dto.JordanParentTaskDTO;
import com.mara.jordan.app.model.dto.JordanStatusDTO;
import com.mara.jordan.app.ui.ReadStatusFragment;
import com.mara.jordan.app.utils.DateUtils;

import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReadStatusAdapter extends ArrayAdapter<JordanStatusDTO> {

    private static final String TAG = "ReadStatusAdapter";
    private final LayoutInflater mInflater;
    private final JordanTaskModel model;
    /**
     * e.g "success" -> @ColorInt (ContextCompat.getColor(context, R.color.success))
     */
    private final Map<String, Integer> colorMapping;
    private float definedTextSizeSp = ReadStatusFragment.StatusTextSizeHelper.DEFAULT_TEXT_SIZE;

    public ReadStatusAdapter(Context ctx, JordanTaskModel model) {
        super(ctx, 0);
        this.model = model;
        mInflater = LayoutInflater.from(ctx);
        colorMapping = makeMapping(ctx,
                R.array.status_color_mapping,
                ctx.getResources().getColor(R.color.status_type_default));
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getStatusId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        //if convertView == null, convertView =. else reuse ???

        View view = mInflater.inflate(R.layout.status_layout, parent, false);
        TextView statusView = view.findViewById(R.id.status_text);
        JordanStatusDTO status = getItem(position);
        String timestamp = DateUtils.formatTimestamp(status.getTimestamp(), false);
        String taskTag = formatTask(status.getParentTask(), true);
        statusView.setText(String.format("%s %s %s", timestamp, taskTag, status.getStatus()));
        statusView.setTextSize(definedTextSizeSp);
        statusView.setTextColor(getStatusColor(status.getType()));

        statusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStatusDetails(status);
            }
        });

        return view;
    }

    private void showStatusDetails(JordanStatusDTO status) {
        String[] details = new String[]{
                status.getStatus(),
                getContext().getString(R.string.status_details_id, status.getStatusId()),
                getContext().getString(R.string.status_details_type, status.getType()),
                getContext().getString(R.string.status_details_timestamp, DateUtils.formatTimestamp(status.getTimestamp(), true)),
                getContext().getString(R.string.status_details_task, formatTask(status.getParentTask(), false))
        };
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.status_details_title)
                .setItems(details, (dialog, which) -> {
                })
                .show();
    }

    private String formatTask(JordanParentTaskDTO parentTask, boolean shortTag) {
        if(shortTag){
            return String.format("[%s]", parentTask.getName());
        }else {
            return String.format("%d, %s %s", parentTask.getTaskId(), parentTask.getName(), (Strings.isNullOrEmpty(parentTask.getState()) ? "" : " (" + parentTask.getState() + ")"));
        }
    }

    @ColorInt
    private int getStatusColor(String type) {
        return colorMapping.containsKey(type) && (colorMapping.get(type) != null) ?
                colorMapping.get(type)
                : ContextCompat.getColor(getContext(), R.color.status_type_default);
    }

    public void changeTextSize(int textSizeSp) {
        definedTextSizeSp = textSizeSp;
        notifyDataSetInvalidated();

    }

    public void refresh(String query, Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter, int depth, JordanReadStatusCallback callback) {
        model.readStatus(depth, callback, new JordanReadStatusCallback() {
            @Override
            public void onStatusLoaded(JordanStatusDTO[] statuses) {
                select(query, typeFilter, taskFilter, statuses);
            }

            @Override
            public void onStatusLoadingError(String errorMessage) {
                Log.e(TAG, errorMessage);
            }
        });
    }

    public void select(String query, Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter) {
        select(query, typeFilter, taskFilter, model.getStatuses());
    }

    private void select(String query, Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter, JordanStatusDTO[] statuses) {
        final Collection<JordanStatusDTO> statusToDisplay = applyFilters(query, typeFilter, taskFilter, statuses);
        clear();
        addAll(statusToDisplay);
    }

    private static List<JordanStatusDTO> applyFilters(String textQuery, Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter, JordanStatusDTO[] statuses) {
        List<JordanStatusDTO> list = new ArrayList<>();
        for (JordanStatusDTO s : statuses) {
            boolean validStatus = true;
            if(!Strings.isNullOrEmpty(textQuery)) {
                validStatus = s.getStatus().toLowerCase().contains(textQuery.toLowerCase());
            }
            boolean validType = true;
            if(!MapUtils.isEmpty(typeFilter)){
                String type = s.getType();
                if(!typeFilter.containsKey(type)) {
                    Log.e(TAG, "Type " + type + " is not handled by type filter (from Dialog). Check StatusFilterTypeAdapter");
                    validType = true;
                } else {
                    validType = typeFilter.get(type);
                }
            }
            boolean validTask = true;
            if(!MapUtils.isEmpty(taskFilter) && s.getParentTask() != null){
                String task = s.getParentTask().getName();
                if(!taskFilter.containsKey(task)) {
                    Log.e(TAG, "Task " + task + " is not handled by task filter (from Dialog). Check StatusFilterTaskAdapter");
                    validTask = true;
                } else {
                    validTask = taskFilter.get(task);
                }
            }
            if(validStatus && validType && validTask){
                list.add(s);
            }
        }
        return Lists.reverse(list); //read as logs : the later the lower (the older the higher)
    }

    /**
     * from https://stackoverflow.com/questions/3013655/creating-hashmap-map-from-xml-resources/40137782#40137782 ?
     */
    private static Map<String, Integer> makeMapping(Context ctx, @ArrayRes int arrayMapping, @ColorInt int defaultValue){
        TypedArray resMapping = ctx.getResources().obtainTypedArray(arrayMapping);
        Map<String, Integer> map = new HashMap<>();
        int len = resMapping.length();
        for(int i = 0 ; i < len ; i++){
            int kvMappingResId = resMapping.getResourceId(i, -1);
            if(kvMappingResId == -1){
                throw new IllegalArgumentException("Invalid array mapping : " + arrayMapping);
            }
            TypedArray kvMapping = ctx.getResources().obtainTypedArray(kvMappingResId);
            if(kvMapping != null) {
                String k = kvMapping.getString(0);
                @SuppressLint("ResourceType")
                int v = kvMapping.getColor(1, defaultValue);
                map.put(k, v);
                kvMapping.recycle();
            }
        }
        resMapping.recycle();
        return map;
    }
}