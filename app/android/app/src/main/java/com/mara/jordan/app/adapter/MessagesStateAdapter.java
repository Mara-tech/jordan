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

public class MessagesStateAdapter extends BaseAdapter {

    private final List<MockDatabase.EasyMessage> mValues;
    private final Context context;
    private LayoutInflater inflater;

    public MessagesStateAdapter(Context ctx, List<MockDatabase.EasyMessage> items) {
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
        convertView = inflater.inflate(R.layout.message_layout, parent, false);

        TextView statusView = convertView.findViewById(R.id.message_action_name);

        statusView.setText(mValues.get(position).getAction().getActionName());
        
        return convertView;
    }

}