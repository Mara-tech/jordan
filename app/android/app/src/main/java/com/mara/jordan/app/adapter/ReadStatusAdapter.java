package com.mara.jordan.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dummy.MockDatabase;
import com.mara.jordan.app.ui.ReadStatusFragment;

import java.text.DateFormat;
import java.util.Date;
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
        String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM).format(new Date(status.getTimestamp()));
        String taskTag = "[" + status.getParentTask().getTaskName() + "]";
        String SPACE = " ";
        statusView.setText(taskTag + SPACE + timestamp + SPACE + status.getStatus());
        statusView.setTextSize(definedTextSizeSp);
        statusView.setTextColor(getStatusColor(status.getType()));

        return convertView;
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