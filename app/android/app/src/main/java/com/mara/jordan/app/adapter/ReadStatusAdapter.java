package com.mara.jordan.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dummy.MockDatabase;

import java.util.List;

public class ReadStatusAdapter extends BaseAdapter {

    private final List<MockDatabase.EasyStatus> mValues;
    private final Context context;
    private LayoutInflater inflater;

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

        statusView.setText(mValues.get(position).getStatus());

        return convertView;
    }

}