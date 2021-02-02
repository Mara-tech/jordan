package com.mara.jordan.app.adapter;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanGetActionsCallback;
import com.mara.jordan.app.api.JordanSendMessageCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.app.model.dto.JordanActionParameterDTO;
import com.mara.jordan.app.model.dto.JordanParentTaskDTO;
import com.mara.jordan.app.ui.JordanSendMessageUiCallback;
import com.mara.jordan.app.utils.CircularProgressButtonHelper;
import com.mara.jordan.app.utils.JordanConstant;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.simplepass.loadingbutton.customViews.CircularProgressButton;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

public class TaskAndActionsAdapter extends ArrayAdapter<JordanActionDefinitionWithTaskDTO> implements StickyListHeadersAdapter, JordanGetActionsCallback, JordanConstant {

    private static final String TAG = "TaskAndActionsAdapter";
    private static final String IS_MANDATORY_INDICATOR = " *";
    private static final String NON_MANDATORY = "";
    private static final long DELAY_BEFORE_REVERT_ACTION_BUTTON_STATE_MS = 2500;
    private static final ActionComparator ACTION_COMPARATOR = new ActionComparator();

    /**
     * model for client, aka root task
     */
    private final JordanTaskModel rootTaskModel;
    private final JordanSendMessageUiCallback callback;
    private final CircularProgressButtonHelper cpbh;
    private LayoutInflater mInflater;
    private final Map<View, Map<JordanActionParameterDTO, View>> actionVisualElementsMapping = new HashMap<>();
    private final Map<Integer, View> viewHolderMapping = new HashMap<>();

    public TaskAndActionsAdapter(Context ctx, JordanTaskModel model, JordanSendMessageUiCallback callback) {
        super(ctx, 0);
        this.rootTaskModel = model;
        mInflater = LayoutInflater.from(ctx);
        this.callback = callback;
        cpbh = CircularProgressButtonHelper.getInstance(ctx);
    }

    @Override
    public long getItemId(int position) {
        return getHashcode(getItem(position));
    }

    private int getHashcode(JordanActionDefinitionWithTaskDTO action) {
        return (action.getActionName() + action.getParentTask().getTaskId()).hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        JordanActionDefinitionWithTaskDTO actionDefinition = getItem(position);
        View view = reuseView(actionDefinition);
        if(view == null) {
            view = createView(actionDefinition, parent);
            setupVisualElements(view, actionDefinition);
        }
        return view;
    }

    private void setupVisualElements(View view, JordanActionDefinitionWithTaskDTO actionDefinition) {
        ViewHolder holder = (ViewHolder) view.getTag();
        holder.actionButton.setText(actionDefinition.getActionName());
        holder.actionButton.setOnClickListener(v -> actionClicked((CircularProgressButton) v, actionDefinition.getParentTask().getTaskId(), actionDefinition.getActionName()));

        final Map<JordanActionParameterDTO, View> placeholdersVisualElements = new HashMap<>();
        actionVisualElementsMapping.put(holder.actionButton, placeholdersVisualElements);

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
                parameterNameView.setText(makeParameterName(parameter));


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

                placeholdersVisualElements.put(parameter, parameterPlaceholderView);

                row++;
                col=0;
            }
        }
    }

    private View reuseView(JordanActionDefinitionWithTaskDTO actionDefinition) {
        int hashcode = getHashcode(actionDefinition);
        return viewHolderMapping.get(hashcode);
    }

    private View createView(JordanActionDefinitionWithTaskDTO actionDefinition, ViewGroup parent) {
        ViewHolder holder = new ViewHolder();
        View view = mInflater.inflate(R.layout.action_layout, parent, false);
        holder.actionButton = view.findViewById(R.id.action_execute);
        holder.actionParametersLayout = view.findViewById(R.id.action_placeholders_container);
        view.setTag(holder);
        int hashcode = getHashcode(actionDefinition);
        viewHolderMapping.put(hashcode, view);
        return view;
    }

    private String makeParameterName(JordanActionParameterDTO parameter) {
        return parameter.getName().concat(parameter.isMandatory() ? IS_MANDATORY_INDICATOR : NON_MANDATORY);
    }

    private void actionClicked(CircularProgressButton buttonClicked, long taskId, String actionName) {
        final Map<JordanActionParameterDTO, View> placeholdersVisualElements = actionVisualElementsMapping.get(buttonClicked);
        final List<JordanActionParameterDTO> missingInput = new ArrayList<>();
        final Map<String, Object> placeholders = new HashMap<>();
        for(JordanActionParameterDTO parameter : placeholdersVisualElements.keySet()){
            String userInput = ((EditText) placeholdersVisualElements.get(parameter)).getText().toString();
            if(parameter.isMandatory() && StringUtils.isEmpty(userInput)){
                missingInput.add(parameter);
            } else {
                placeholders.put(parameter.getName(), userInput);
            }
        }
        if(!missingInput.isEmpty()){
            callback.alertMandatoryFieldMissing(missingInput);
        } else {
            buttonClicked.startAnimation();
            rootTaskModel.subTaskModel(taskId).sendMessage(actionName, placeholders, callback,
                            new JordanSendMessageCallback() {
                                @Override
                                public void onMessageSent(long messageId) {
                                    cpbh.successAndReset(buttonClicked);
                                }

                                @Override
                                public void onMessageSendingError(String errorMessage) {
                                    cpbh.errorAndReset(buttonClicked);
                                }
                            }
                            );
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


        if(parentTask.getState() != null && TASK_COMPLETE_STATE.equals(parentTask.getState())){
            holder.taskNameView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.task_complete, 0);
        } else {
            holder.taskNameView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }

        if(parentTask.getProgress() != null){
            holder.taskProgressBar.setVisibility(View.VISIBLE);
            holder.taskProgressBar.setProgress(parentTask.getProgress());
        }else {
            holder.taskProgressBar.setVisibility(View.INVISIBLE);
        }

        convertView.setOnLongClickListener(v -> displayTaskOptions(v, parentTask));

        return convertView;
    }

    private boolean displayTaskOptions(View view, JordanParentTaskDTO parentTask) {
        PopupMenu popup = new PopupMenu(getContext(), view, Gravity.END);
        popup.getMenuInflater().inflate(R.menu.task_popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()){
                    case R.id.infos_task:
                        showInfosDialog(parentTask);
                        break;
                    default:
                        Log.e(TAG, "Unhandled menu item " + item.getTitle());
                }
                return true;
            }
        });
        popup.show();
        return true;
    }

    private void showInfosDialog(JordanParentTaskDTO parentTask) {
        List<String> details = Lists.newArrayList(
                getContext().getString(R.string.task_details_id, parentTask.getTaskId()),
                getContext().getString(R.string.task_details_state, parentTask.getState()));

        if(parentTask.getProgress() != null) {
            details.add(getContext().getString(R.string.task_details_progress, parentTask.getProgress()));
        }
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(parentTask.getName())
                .setItems(details.toArray(new String[]{}), (dialog, which) -> {
                })
                .show();

    }

    @Override
    public long getHeaderId(int position) {
        return getItem(position).getParentTask().getTaskId();
    }

    public void refresh(JordanGetActionsCallback callback) {
        rootTaskModel.readActionDefinitions(callback, this);
    }

    @Override
    public void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions) {
        Arrays.sort(actions, ACTION_COMPARATOR);
        display(actions);
    }

    @Override
    public void clear() {
        super.clear();
        viewHolderMapping.clear();
        actionVisualElementsMapping.clear();
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
        CircularProgressButton actionButton;
        GridLayout actionParametersLayout;
    }


}