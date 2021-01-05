package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanGetActionsCallback;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.app.model.dto.JordanActionParameterDTO;
import com.mara.jordan.app.model.dto.JordanParentTaskDTO;

import java.util.HashMap;
import java.util.Map;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

public class TaskAndActionsAdapter extends ArrayAdapter<JordanActionDefinitionWithTaskDTO> implements StickyListHeadersAdapter, JordanGetActionsCallback {

    private static final String TAG = "TaskAndActionsAdapter";

    private final JordanClientModel model;
    private LayoutInflater mInflater;
    private final Map<View, Map<String, View>> actionVisualElements = new HashMap<>();

    public TaskAndActionsAdapter(Context ctx, JordanClientModel model) {
        super(ctx, 0);
        this.model = model;
        mInflater = LayoutInflater.from(ctx);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.action_layout, parent, false);
            holder.actionButton = convertView.findViewById(R.id.action_execute);
            holder.actionParametersLayout = convertView.findViewById(R.id.action_placeholders_container);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        JordanActionDefinitionWithTaskDTO actionDefinition = getItem(position);

        holder.actionButton.setText(actionDefinition.getActionName());
        holder.actionButton.setOnClickListener(v -> actionClicked(v));

        final Map<String, View> placeholdersVisualElements = new HashMap<>();
        actionVisualElements.put(holder.actionButton, placeholdersVisualElements);

        holder.actionParametersLayout.removeAllViews();

        if(actionDefinition.getParameters() != null) {
            int row=0, col=0;
            for (JordanActionParameterDTO parameter : actionDefinition.getParameters()) {
                final TextView parameterNameView = new TextView(getContext());
                holder.actionParametersLayout.addView(parameterNameView,
                        new GridLayout.LayoutParams(
                                GridLayout.spec(row),
                                GridLayout.spec(col, 1f)
                        ));
                parameterNameView.setText(parameter.getName());


                col ++;

                final EditText parameterPlaceholderView = new EditText(getContext()); //TODO change view type according to parameter type (e.g int picker). + setInputType
                parameterPlaceholderView.setSingleLine();
                final GridLayout.LayoutParams parameterPlaceholderLayoutParams = new GridLayout.LayoutParams(
                        GridLayout.spec(row),
                        GridLayout.spec(col, 3f)
                );
                parameterPlaceholderView.setGravity(Gravity.CENTER_HORIZONTAL);
                holder.actionParametersLayout.addView(parameterPlaceholderView,
                        parameterPlaceholderLayoutParams);
                if (parameter.getDefaultValue() != null) {
                    parameterPlaceholderView.setText(String.valueOf(parameter.getDefaultValue()));
                }

                placeholdersVisualElements.put(parameter.getName(), parameterPlaceholderView);

                row++;
                col=0;
            }
        }

        return convertView;
    }

    private void actionClicked(View buttonClicked) {
        final Map<String, View> placeholders = actionVisualElements.get(buttonClicked);
        for(String parameterName : placeholders.keySet()){
            Log.i("ACTION", parameterName + " -> " + ((EditText)placeholders.get(parameterName)).getText());
        }
    }

    @Override
    public View getHeaderView(int position, View convertView, ViewGroup parent) {
        JordanParentTaskDTO parentTask = getItem(position).getParentTask();
        HeaderViewHolder holder;
        if (convertView == null) {
            holder = new HeaderViewHolder();
            convertView = mInflater.inflate(R.layout.task_layout, parent, false);
            holder.taskNameView = convertView.findViewById(R.id.task_name);
            holder.taskProgressBar = convertView.findViewById(R.id.task_progress_bar);
            convertView.setTag(holder);
        } else {
            holder = (HeaderViewHolder) convertView.getTag();
        }
        String headerText = parentTask.getName();
        holder.taskNameView.setText(headerText);


        if(parentTask.getProgress() != null){
            holder.taskProgressBar.setVisibility(View.VISIBLE);
            holder.taskProgressBar.setProgress(parentTask.getProgress());
        }else {
            holder.taskProgressBar.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }

    @Override
    public long getHeaderId(int position) {
        return getItem(position).getParentTask().getTaskId();
    }

    public void refresh(JordanGetActionsCallback callback) {
        model.readActionDefinitions(callback, this);
    }

    @Override
    public void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions) {
        display(actions);
    }

    private void display(JordanActionDefinitionWithTaskDTO[] actionsToDisplay) {
        clear();
        addAll(actionsToDisplay);
    }
    @Override
    public void onActionsLoadingError(String errorMessage) {
        Log.e(TAG, errorMessage);
    }

    class HeaderViewHolder {
        TextView taskNameView;
        ProgressBar taskProgressBar;
    }

    class ViewHolder {
        Button actionButton;
        GridLayout actionParametersLayout;
    }


}