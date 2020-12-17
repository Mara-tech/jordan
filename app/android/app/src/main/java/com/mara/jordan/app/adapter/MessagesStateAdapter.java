package com.mara.jordan.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dummy.MockDatabase;
import com.mara.jordan.app.utils.DateUtils;

import org.apache.commons.collections4.MapUtils;

import java.util.List;

public class MessagesStateAdapter extends BaseAdapter {

    private static final MockDatabase.EasyMessageState DEFAULT_MESSAGE = MockDatabase.EasyMessageState.builder().build();
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

        TextView actionName = convertView.findViewById(R.id.message_action_name);
        TextView author = convertView.findViewById(R.id.message_author);
        TextView currentState = convertView.findViewById(R.id.message_current_state);

        MockDatabase.EasyMessage message = mValues.get(position);
        actionName.setText(message.getAction().getActionName());
        author.setText(message.getAuthor());
        currentState.setText(getCurrentState(message.getAudit()));

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessageDetails(message);
            }
        });

        return convertView;
    }

    private void showMessageDetails(MockDatabase.EasyMessage message) {
        List<String> details = Lists.newArrayList(
                message.getAction().getActionName(),
                context.getString(R.string.message_state_detais_id) + message.getId(),
                context.getString(R.string.message_state_detais_author) + message.getAuthor(),
                context.getString(R.string.message_state_detais_audit)
        );

        for(MockDatabase.EasyMessageState previousState : message.getAudit()){
            details.add("  " + DateUtils.formatTimestamp(previousState.getTimestamp(), false) + "  " + previousState.getState());
        };

        if(MapUtils.isNotEmpty(message.getAction().getPlaceholders())){
            details.add(context.getString(R.string.message_state_detais_placeholders));
            for(String parameterName : message.getAction().getPlaceholders().keySet()){
                details.add("  " + parameterName + " -> " + message.getAction().getPlaceholders().get(parameterName));
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setItems(details.toArray(new String[]{}), (dialog, which) -> {})
                .create().show();
    }

    private String getCurrentState(List<MockDatabase.EasyMessageState> audit) {
        //or last element if the list list construction ensures chronology
//        return audit.stream().reduce((m1,m2) -> m1.getTimestamp() > m2.getTimestamp() ? m1 : m2).orElse(DEFAULT_MESSAGE).getState();
        boolean seen = false;
        MockDatabase.EasyMessageState acc = null;
        for (MockDatabase.EasyMessageState easyMessageState : audit) {
            if (!seen) {
                seen = true;
                acc = easyMessageState;
            } else {
                acc = acc.getTimestamp() > easyMessageState.getTimestamp() ? acc : easyMessageState;
            }
        }
        return (seen ? acc : DEFAULT_MESSAGE).getState();
    }

}