package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanGetClientsCallback;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.dto.JordanClientDTO;
import com.mara.jordan.app.ui.OnClientClickListener;
import com.mara.jordan.app.utils.JordanConstant;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

import static com.mara.jordan.app.utils.JordanHelper.NO_TASK_WITH_PROGRESS_FLAG;
import static com.mara.jordan.app.utils.JordanHelper.estimateClientProgress;
import static com.mara.jordan.app.utils.JordanHelper.extractActiveTasksInfo;

public class ClientAdapter extends ArrayAdapter<JordanClientDTO> implements StickyListHeadersAdapter, JordanGetClientsCallback, JordanConstant {

    private static final String TAG = "ClientAdapter";
    private final OnClientClickListener clickListener;
    private JordanClientModel model;
    private final LayoutInflater mInflater;

    public ClientAdapter(Context ctx, JordanClientModel model, OnClientClickListener clickListener) {
        super(ctx, 0);
        this.model = model;
        mInflater = LayoutInflater.from(ctx);
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = mInflater.inflate(R.layout.client_item_layout, parent, false);
        TextView clientName = view.findViewById(R.id.client_name);
        TextView runningTasks = view.findViewById(R.id.client_running_tasks);
        ProgressBar clientProgress = view.findViewById(R.id.client_progress);

        JordanClientDTO client = getItem(position);
        clientName.setText(client.getName());
        String[] activeTaskStates = getContext().getResources().getStringArray(R.array.active_task_states);
        runningTasks.setText(getContext().getString(R.string.client_running_task, extractActiveTasksInfo(client, activeTaskStates)));
        int progress = estimateClientProgress(client, activeTaskStates);
        if(progress == NO_TASK_WITH_PROGRESS_FLAG){
            clientProgress.setVisibility(View.INVISIBLE);
        } else {
            clientProgress.setVisibility(View.VISIBLE);
            clientProgress.setProgress(progress);
        }

        view.setOnClickListener(v -> clickListener.onClientClicked(client));

        return view;
    }

    public void refresh(JordanGetClientsCallback callback) {
        model.listClients(callback, this);
    }

    @Override
    public void onClientsLoaded(JordanClientDTO[] clients) {
        //TODO sort by state, name, progress ?
        display(clients);
    }

    private void display(JordanClientDTO[] clients) {
        clear();
        addAll(clients);
    }

    @Override
    public void onClientsLoadingError(String errorMessage) {
        Log.e(TAG, errorMessage);
    }


    //Sticky header by client state
    @Override
    public View getHeaderView(int position, View convertView, ViewGroup parent) {
        View v = mInflater.inflate(R.layout.client_header_layout, parent, false);
        TextView header = v.findViewById(R.id.client_header_name);
        header.setText(getHeader(position));
        return v;
    }

    @Override
    public long getHeaderId(int position) {
        return getHeader(position).hashCode();
    }

    private String getHeader(int position) {
        return getItem(position).getState();
    }
}
