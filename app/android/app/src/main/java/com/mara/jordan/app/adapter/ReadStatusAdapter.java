package com.mara.jordan.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dummy.MockDatabase;
import com.mara.jordan.app.ui.ReadStatusFragment;
import com.mara.jordan.app.utils.DateUtils;

import java.util.List;

public class ReadStatusAdapter extends BaseAdapter {

    private final List<MockDatabase.EasyStatus> mValues;
    private final Context context;
    private LayoutInflater inflater;

    public static final String STATUS_TYPE_SUCCESS = "success";
    public static final String STATUS_TYPE_FAILURE = "failure";
    public static final String STATUS_TYPE_GENERAL = "general";
    public static final String STATUS_TYPE_PROGRESS = "progress";
    private float definedTextSizeSp = ReadStatusFragment.StatusTextSizeHelper.DEFAULT_TEXT_SIZE;

    public ReadStatusAdapter(Context ctx, List<MockDatabase.EasyStatus> items) {
        this.context = ctx;
        mValues = items;
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return mValues.size();
    }

    @Override
    public Object getItem(int position) {
        return mValues.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = inflater.inflate(R.layout.status_layout, parent, false);

        TextView statusView = convertView.findViewById(R.id.status_text);
        MockDatabase.EasyStatus status = mValues.get(position);
        String timestamp = DateUtils.formatTimestamp(status.getTimestamp(), false);
        String taskTag = "[" + status.getParentTask().getTaskName() + "]";
        String SPACE = " ";
        statusView.setText(taskTag + SPACE + timestamp + SPACE + status.getStatus());
        statusView.setTextSize(definedTextSizeSp);
        statusView.setTextColor(getStatusColor(status.getType()));

        statusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStatusDetails(status);
            }
        });

        return convertView;
    }

    private void showStatusDetails(MockDatabase.EasyStatus status) {
        String[] details = new String[]{
                status.getStatus(),
                context.getString(R.string.status_detais_id) + status.getId(),
                context.getString(R.string.status_detais_type) + status.getType(),
                context.getString(R.string.status_detais_timestamp) + DateUtils.formatTimestamp(status.getTimestamp(), true),
                context.getString(R.string.status_detais_task) + formatTask(status.getParentTask())
        };
        new MaterialAlertDialogBuilder(context)
                .setItems(details, (dialog, which) -> {})
                .create().show();
    }

    private String formatTask(MockDatabase.EasyTask parentTask) {
        return parentTask.getTaskId() + ", " + parentTask.getTaskName() + (parentTask.getProgress() != null ? " ("+parentTask.getProgress() + "%)" : "");
    }

    private int getStatusColor(String type) {
        int colorResId;
        switch (type){
            case STATUS_TYPE_SUCCESS:
                colorResId = R.color.status_type_success;
                break;
            case STATUS_TYPE_FAILURE:
                colorResId = R.color.status_type_failure;
                break;
            case STATUS_TYPE_GENERAL:
                colorResId =  R.color.status_type_general;
                break;
            case STATUS_TYPE_PROGRESS:
                colorResId = R.color.status_type_progress;
                break;
            default:
                colorResId = R.color.status_type_default;
                break;
        }
        return ContextCompat.getColor(context, colorResId);
    }

    public void changeTextSize(int textSizeSp) {
        definedTextSizeSp = textSizeSp;
        notifyDataSetInvalidated();

    }
}