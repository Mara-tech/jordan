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
import com.mara.jordan.app.model.dto.JordanTaskDTO;
import com.mara.jordan.app.ui.OnClientClickListener;
import com.mara.jordan.app.utils.JordanConstant;

import java.util.ArrayList;
import java.util.List;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

import static com.google.common.math.Stats.meanOf;

public class ClientAdapter extends ArrayAdapter<JordanClientDTO> implements StickyListHeadersAdapter, JordanGetClientsCallback, JordanConstant {

    private static final String TAG = "ClientAdapter";
    private static final int NO_TASK_WITH_PROGRESS_FLAG = -1;
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
        runningTasks.setText(getContext().getString(R.string.client_running_task, extractRunningTasks(client)));
        int progress = extractRunningTasksProgress(client);
        if(progress == NO_TASK_WITH_PROGRESS_FLAG){
            clientProgress.setVisibility(View.INVISIBLE);
        } else {
            clientProgress.setVisibility(View.VISIBLE);
            clientProgress.setProgress(progress);
        }

        view.setOnClickListener(v -> clickListener.onClientClicked(client));

        return view;
    }

    private int extractRunningTasksProgress(JordanClientDTO client) {
//        List<Integer> eligibleTaskProgress = client.getTasks().stream().filter(t -> RUNNING_STATE.equals(t.getState()) && t.getProgress() != null).map(JordanTaskDTO::getProgress).collect(Collectors.toList());
        List<Integer> eligibleTaskProgress = new ArrayList<>();
        for (JordanTaskDTO t : client.getTasks()) {
            if (RUNNING_STATE.equals(t.getState()) && t.getProgress() != null) {
                Integer progress = t.getProgress();
                eligibleTaskProgress.add(progress);
            }
        }
        if(eligibleTaskProgress.size() == 0){
            return NO_TASK_WITH_PROGRESS_FLAG;
        }
        return (int) meanOf(eligibleTaskProgress); //no need to round
    }

    /**
     * return items :
     * <ol>
     *     <li>number of running tasks (state=RUNNING) in the client</li>
     * </ol>
     */
    private Object[] extractRunningTasks(JordanClientDTO client) {
//        long runningTaskCount = client.getTasks().stream().filter(t -> RUNNING_STATE.equals(t.getState())).count();
        long runningTaskCount = 0L;
        for (JordanTaskDTO t : client.getTasks()) {
            if (RUNNING_STATE.equals(t.getState())) {
                runningTaskCount++;
            }
        }
        return new Object[]{String.valueOf(runningTaskCount)};
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
