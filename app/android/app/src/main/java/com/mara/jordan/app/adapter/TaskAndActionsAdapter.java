package com.mara.jordan.app.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanGetActionsCallback;
import com.mara.jordan.app.api.JordanSendMessageCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.app.model.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.app.model.dto.JordanActionParameterDTO;
import com.mara.jordan.app.model.dto.JordanParentTaskDTO;
import com.mara.jordan.app.ui.JordanSendMessageUiCallback;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.simplepass.loadingbutton.customViews.CircularProgressButton;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

public class TaskAndActionsAdapter extends ArrayAdapter<JordanActionDefinitionWithTaskDTO> implements StickyListHeadersAdapter, JordanGetActionsCallback{

    private static final String TAG = "TaskAndActionsAdapter";
    private static final String IS_MANDATORY_INDICATOR = " *";
    private static final String NON_MANDATORY = "";
    private static final long DELAY_BEFORE_REVERT_ACTION_BUTTON_STATE_MS = 2500;

    /**
     * model for client, aka root task
     */
    private final JordanTaskModel rootTaskModel;
    private final JordanSendMessageUiCallback callback;
    private LayoutInflater mInflater;
    private final Map<View, Map<JordanActionParameterDTO, View>> actionVisualElementsMapping = new HashMap<>();
    private Map<Integer, View> viewHolderMapping = initViewHolderMapping();

    private static Map<Integer, View> initViewHolderMapping() {
        return new HashMap<>();
    }

    public TaskAndActionsAdapter(Context ctx, JordanTaskModel model, JordanSendMessageUiCallback callback) {
        super(ctx, 0);
        this.rootTaskModel = model;
        mInflater = LayoutInflater.from(ctx);
        this.callback = callback;
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
        View view = createOrReuseView(actionDefinition, parent);
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

        return view;
    }

    private View createOrReuseView(JordanActionDefinitionWithTaskDTO actionDefinition, ViewGroup parent) {
        int hashcode = getHashcode(actionDefinition);
        View reuseView = viewHolderMapping.get(hashcode);
        if(reuseView == null){
            reuseView = createView(parent);
            viewHolderMapping.put(hashcode, reuseView);
        }
        return reuseView;
    }

    private View createView(ViewGroup parent) {
        ViewHolder holder = new ViewHolder();
        View view = mInflater.inflate(R.layout.action_layout, parent, false);
        holder.actionButton = view.findViewById(R.id.action_execute);
        holder.actionParametersLayout = view.findViewById(R.id.action_placeholders_container);
        view.setTag(holder);
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
                                    buttonClicked.doneLoadingAnimation(getProgressionButtonFillColor(), getSuccessBitmap());
                                    waitAndResetButton(buttonClicked);
                                }

                                private void waitAndResetButton(CircularProgressButton button) {
                                    new Handler().postDelayed(button::revertAnimation, DELAY_BEFORE_REVERT_ACTION_BUTTON_STATE_MS);
                                }

                                @Override
                                public void onMessageSendingError(String errorMessage) {
                                    buttonClicked.doneLoadingAnimation(getProgressionButtonFillColor(), getErrorBitmap());
                                    waitAndResetButton(buttonClicked);
                                }
                            }
                            );
        }
    }

    private int getProgressionButtonFillColor() {
        return ContextCompat.getColor(getContext(), R.color.red_bull);
    }

    private Bitmap getSuccessBitmap() {
        return drawBitmap(R.drawable.check);
    }

    private Bitmap getErrorBitmap() {
        return drawBitmap(R.drawable.cross);
    }

    private Bitmap drawBitmap(@DrawableRes int resId){
        Drawable drawable = ContextCompat.getDrawable(getContext(), resId);
        try {
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Cannot create bitmap from resource " + resId, e);
            return Bitmap.createBitmap(0,0, Bitmap.Config.ALPHA_8);
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
        rootTaskModel.readActionDefinitions(callback, this);
    }

    @Override
    public void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions) {
        display(actions);
    }

    private void display(JordanActionDefinitionWithTaskDTO[] actionsToDisplay) {
        clear();
        viewHolderMapping = initViewHolderMapping();
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