package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mara.jordan.app.R;
import com.mara.jordan.app.model.dummy.MockDatabase;
import com.mara.jordan.app.model.dummy.MockDatabase.EasyActionDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

public class TaskAndActionsAdapter extends BaseAdapter implements StickyListHeadersAdapter {

    private final List<EasyActionDefinition> mValues;
    private final Context context;
    private LayoutInflater inflater;
    private final Map<View, Map<String, View>> actionVisualElements = new HashMap<>();

    public TaskAndActionsAdapter(Context ctx, List<EasyActionDefinition> items) {
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
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = inflater.inflate(R.layout.action_layout, parent, false);
            holder.actionButton = convertView.findViewById(R.id.action_execute);
            holder.actionParametersLayout = convertView.findViewById(R.id.action_placeholders_container);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.actionButton.setText(mValues.get(position).getActionName());
        holder.actionButton.setOnClickListener(v -> actionClicked(v));

        final Map<String, View> placeholdersVisualElements = new HashMap<>();
        actionVisualElements.put(holder.actionButton, placeholdersVisualElements);

        holder.actionParametersLayout.removeAllViews();

        if(mValues.get(position).getParameters() != null) {
            int row=0, col=0;
            for (MockDatabase.EasyActionParameter parameter : mValues.get(position).getParameters()) {
                final TextView parameterNameView = new TextView(context);
                holder.actionParametersLayout.addView(parameterNameView,
                        new GridLayout.LayoutParams(
                                GridLayout.spec(row),
                                GridLayout.spec(col, 1f)
                        ));
                parameterNameView.setText(parameter.getParameterName());


                col ++;

                final EditText parameterPlaceholderView = new EditText(context); //TODO change view type according to parameter type (e.g int picker). + setInputType
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

                placeholdersVisualElements.put(parameter.getParameterName(), parameterPlaceholderView);

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
        HeaderViewHolder holder;
        if (convertView == null) {
            holder = new HeaderViewHolder();
            convertView = inflater.inflate(R.layout.task_layout, parent, false);
            holder.taskNameView = convertView.findViewById(R.id.task_name);
            holder.taskProgressBar = convertView.findViewById(R.id.task_progress_bar);
            convertView.setTag(holder);
        } else {
            holder = (HeaderViewHolder) convertView.getTag();
        }
        //set header text as first char in name
        String headerText = mValues.get(position).getParentTask().getTaskName();
        holder.taskNameView.setText(headerText);


        if(mValues.get(position).getParentTask().getProgress() != null){
            holder.taskProgressBar.setVisibility(View.VISIBLE);
            holder.taskProgressBar.setProgress(mValues.get(position).getParentTask().getProgress());
        }else {
            holder.taskProgressBar.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }

    @Override
    public long getHeaderId(int position) {
        //return the first character of the country as ID because this is what headers are based upon
        return mValues.get(position).getParentTask().getTaskId();
    }

    class HeaderViewHolder {
        TextView taskNameView;
        ProgressBar taskProgressBar;
    }

    class ViewHolder {
        Button actionButton;
        GridLayout actionParametersLayout;
    }


//    public class ViewHolder extends RecyclerView.ViewHolder {
//        public final View mView;
//        public final TextView mContentView;
//        public DummyItem mItem;
//
//        public ViewHolder(View view) {
//            super(view);
//            mView = view;
//            mContentView = (Button) view.findViewById(R.id.action_execute);
//        }
//
//        @Override
//        public String toString() {
//            return super.toString() + " '" + mContentView.getText() + "'";
//        }
//    }
}