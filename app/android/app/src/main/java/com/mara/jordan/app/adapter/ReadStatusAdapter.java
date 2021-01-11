package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
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
import java.util.List;
import java.util.Map;

public class ReadStatusAdapter extends ArrayAdapter<JordanStatusDTO> {

    public static final String STATUS_TYPE_SUCCESS = "success";
    public static final String STATUS_TYPE_FAILURE = "failure";
    public static final String STATUS_TYPE_GENERAL = "general";
    public static final String STATUS_TYPE_PROGRESS = "progress";
    private static final String TAG = "ReadStatusAdapter";
    private final LayoutInflater mInflater;
    private final JordanTaskModel model;
    private float definedTextSizeSp = ReadStatusFragment.StatusTextSizeHelper.DEFAULT_TEXT_SIZE;

    public ReadStatusAdapter(Context ctx, JordanTaskModel model) {
        super(ctx, 0);
        this.model = model;
        mInflater = LayoutInflater.from(ctx);
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
        statusView.setText(String.format("%s %s %s", taskTag, timestamp, status.getStatus()));
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
                getContext().getString(R.string.status_details_id, status.getStatusId()),
                getContext().getString(R.string.status_details_type, status.getType()),
                getContext().getString(R.string.status_details_timestamp, DateUtils.formatTimestamp(status.getTimestamp(), true)),
                getContext().getString(R.string.status_details_task, formatTask(status.getParentTask(), false))
        };
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(status.getStatus())
                .setItems(details, (dialog, which) -> {
                })
                .create().show();
    }

    private String formatTask(JordanParentTaskDTO parentTask, boolean shortTag) {
        if(shortTag){
            return String.format("[%s]", parentTask.getName());
        }else {
            return String.format("%d, %s %s", parentTask.getTaskId(), parentTask.getName(), (Strings.isNullOrEmpty(parentTask.getState()) ? "" : " (" + parentTask.getState() + ")"));
        }
    }

    //TODO use xml mapping ?
    private int getStatusColor(String type) {
        int colorResId;
        switch (type) {
            case STATUS_TYPE_SUCCESS:
                colorResId = R.color.status_type_success;
                break;
            case STATUS_TYPE_FAILURE:
                colorResId = R.color.status_type_failure;
                break;
            case STATUS_TYPE_GENERAL:
                colorResId = R.color.status_type_general;
                break;
            case STATUS_TYPE_PROGRESS:
                colorResId = R.color.status_type_progress;
                break;
            default:
                colorResId = R.color.status_type_default;
                break;
        }
        return ContextCompat.getColor(getContext(), colorResId);
    }

    public void changeTextSize(int textSizeSp) {
        definedTextSizeSp = textSizeSp;
        notifyDataSetInvalidated();

    }

    public void refresh(String query, Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter, JordanReadStatusCallback callback) {
        model.readStatus(callback, new JordanReadStatusCallback() {
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
        return list;
    }
}