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
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.app.model.dto.JordanMessageStateAuditDTO;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;
import com.mara.jordan.app.utils.DateUtils;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MessagesStateAdapter extends ArrayAdapter<JordanMessageStateDTO> {

    private static final String TAG = "MessageStateAdapter";
    private final JordanTaskModel model;
    private LayoutInflater mInflater;
    private static final JordanMessageStateAuditDTO DEFAULT_MESSAGE = JordanMessageStateAuditDTO.builder().build();

    public MessagesStateAdapter(Context ctx, JordanTaskModel model) {
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
        TextView parentTask = view.findViewById(R.id.message_parent_task);
        TextView currentState = view.findViewById(R.id.message_current_state);

        JordanMessageStateDTO message = getItem(position);
        actionName.setText(message.getAction().getActionName());
        author.setText(message.getAuthor());
        if(message.getParentTask() != null){
            parentTask.setText(StringUtils.defaultIfEmpty(message.getParentTask().getName(), ""));
        }
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
                getContext().getString(R.string.message_state_details_author, message.getAuthor())
        );

        if(message.getParentTask() != null){
            StringBuilder taskDescription = new StringBuilder();
            taskDescription.append(getContext().getString(R.string.message_state_details_parent_task,
                    message.getParentTask().getName(),
                    message.getParentTask().getTaskId()));
            boolean hasState = StringUtils.isNotEmpty(message.getParentTask().getState());
            boolean hasProgress = message.getParentTask().getProgress() != null;
            if(hasState || hasProgress){
                taskDescription.append(getContext().getString(R.string.message_state_details_parent_task_introduce_state_or_progress));
            }
            if(hasState){
                taskDescription.append(getContext().getString(R.string.message_state_details_parent_task_state, message.getParentTask().getState()));
            }
            if(hasProgress){
                taskDescription.append(getContext().getString(R.string.message_state_details_parent_task_progress, message.getParentTask().getProgress()));
            }
            details.add(taskDescription.toString());
        }

        details.add(getContext().getString(R.string.message_state_details_audit));
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

    public static String getCurrentState(List<JordanMessageStateAuditDTO> audit) {
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

    public void refresh(JordanReadMessagesCallback callback, Map<String, Boolean> taskFilter, Map<String, Boolean> authorFilter, Map<String, Boolean> stateFilter) {
        model.readMessages(callback, new JordanReadMessagesCallback() {
            @Override
            public void onMessagesLoaded(JordanMessageStateDTO[] messages) {
                select(taskFilter, authorFilter, stateFilter, messages);
            }

            @Override
            public void onMessagesLoadingError(String errorMessage) {
                Log.e(TAG, errorMessage);
            }

        });
    }

    public void select(Map<String, Boolean> taskFilter, Map<String, Boolean> authorFilter, Map<String, Boolean> stateFilter) {
        select(taskFilter, authorFilter, stateFilter, model.getMessages());
    }

    private void select(Map<String, Boolean> taskFilter, Map<String, Boolean> authorFilter, Map<String, Boolean> stateFilter, JordanMessageStateDTO[] messages) {
        final Collection<JordanMessageStateDTO> messagesToDisplay = applyFilters(taskFilter, authorFilter, stateFilter, messages);
        clear();
        addAll(messagesToDisplay);
    }

    private static Collection<JordanMessageStateDTO> applyFilters(Map<String, Boolean> taskFilter, Map<String, Boolean> authorFilter, Map<String, Boolean> stateFilter, JordanMessageStateDTO[] messages) {
        List<JordanMessageStateDTO> list = new ArrayList<>();
        for(JordanMessageStateDTO m : messages){
            boolean validTask = true;
            if(!MapUtils.isEmpty(taskFilter) && m.getParentTask() != null){
                String task = m.getParentTask().getName();
                if(!taskFilter.containsKey(task)){
                    Log.e(TAG, "Task " + task + " is not handled by task filter (from Dialog). Check MessageFilterTaskAdapter");
                    validTask = true;
                } else {
                    validTask = taskFilter.get(task);
                }
            }

            boolean validAuthor = true;
            if(!MapUtils.isEmpty(authorFilter)){
                String author = m.getAuthor();
                if(!authorFilter.containsKey(author)){
                    Log.e(TAG, "Author " + author + " is not handled by author filter (from Dialog). Check MessageFilterAuthorAdapter");
                    validAuthor = true;
                } else {
                    validAuthor = authorFilter.get(author);
                }
            }

            boolean validState = true;
            if(!MapUtils.isEmpty(stateFilter) && m.getAudit() != null){
                String state = getCurrentState(m.getAudit());
                if(!stateFilter.containsKey(state)){
                    Log.e(TAG, "State " + state + " is not handled by state filter (from Dialog). Check MessageFilterStateAdapter");
                    validState = true;
                } else {
                    validState = stateFilter.get(state);
                }
            }
            if(validTask && validAuthor && validState){
                list.add(m);
            }
        }
        return list;
    }
}