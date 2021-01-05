package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanReadMessagesCallback;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.dto.JordanMessageStateAuditDTO;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.utils.DateUtils;

import org.apache.commons.collections4.MapUtils;

import java.util.List;

public class MessagesStateAdapter extends ArrayAdapter<JordanMessageStateDTO> implements JordanReadMessagesCallback {

    private static final String TAG = "MessageStateAdapter";
    private final JordanClientModel model;
    private LayoutInflater mInflater;
    private static final JordanMessageStateAuditDTO DEFAULT_MESSAGE = JordanMessageStateAuditDTO.builder().build();

    public MessagesStateAdapter(Context ctx, JordanClientModel model) {
        super(ctx, 0);
        this.model = model;
        mInflater = LayoutInflater.from(ctx);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getMessageId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.message_layout, parent, false);

        TextView actionName = view.findViewById(R.id.message_action_name);
        TextView author = view.findViewById(R.id.message_author);
        TextView currentState = view.findViewById(R.id.message_current_state);

        JordanMessageStateDTO message = getItem(position);
        actionName.setText(message.getAction().getActionName());
        author.setText(message.getAuthor());
        currentState.setText(getCurrentState(message.getAudit()));

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessageDetails(message);
            }
        });

        return view;
    }

    private void showMessageDetails(JordanMessageStateDTO message) {
        List<String> details = Lists.newArrayList(
                message.getAction().getActionName(),
                getContext().getString(R.string.message_state_details_id, message.getMessageId()),
                getContext().getString(R.string.message_state_details_author, message.getAuthor()),
                getContext().getString(R.string.message_state_details_audit)
        );

        for(JordanMessageStateAuditDTO previousState : message.getAudit()){
            details.add("  " + DateUtils.formatTimestamp(previousState.getTimestamp(), false) + "  " + previousState.getState());
        };

        if(MapUtils.isNotEmpty(message.getAction().getPlaceholders())){
            details.add(getContext().getString(R.string.message_state_details_placeholders));
            for(String parameterName : message.getAction().getPlaceholders().keySet()){
                details.add("  " + parameterName + " -> " + message.getAction().getPlaceholders().get(parameterName));
            }
        }

        new MaterialAlertDialogBuilder(getContext())
                .setItems(details.toArray(new String[]{}), (dialog, which) -> {})
                .create().show();
    }

    private String getCurrentState(List<JordanMessageStateAuditDTO> audit) {
        //or last element if the list list construction ensures chronology
//        return audit.stream().reduce((m1,m2) -> m1.getTimestamp() > m2.getTimestamp() ? m1 : m2).orElse(DEFAULT_MESSAGE).getState();
        boolean seen = false;
        JordanMessageStateAuditDTO acc = null;
        for (JordanMessageStateAuditDTO jordanMessageStateAuditDTO : audit) {
            if (!seen) {
                seen = true;
                acc = jordanMessageStateAuditDTO;
            } else {
                acc = acc.getTimestamp() > jordanMessageStateAuditDTO.getTimestamp() ? acc : jordanMessageStateAuditDTO;
            }
        }
        return (seen ? acc : DEFAULT_MESSAGE).getState();
    }

    public void refresh(JordanReadMessagesCallback callback) {
        model.readMessages(callback, this);
    }

    @Override
    public void onMessagesLoaded(JordanMessageStateDTO[] messages) {
        display(messages);
    }

    private void display(JordanMessageStateDTO[] messagesToDisplay) {
        clear();
        addAll(messagesToDisplay);
    }

    @Override
    public void onMessagesLoadingError(String errorMessage) {
        Log.e(TAG, errorMessage);
    }
}