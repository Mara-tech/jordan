package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanGetClientsCallback;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.dto.JordanClientDTO;
import com.mara.jordan.app.model.dto.JordanTaskDTO;
import com.mara.jordan.app.ui.ClientDeletionCallback;
import com.mara.jordan.app.ui.OnClientClickListener;
import com.mara.jordan.app.utils.JordanConstant;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.List;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

import static com.mara.jordan.app.utils.JordanHelper.NO_TASK_WITH_PROGRESS_FLAG;
import static com.mara.jordan.app.utils.JordanHelper.estimateClientProgress;
import static com.mara.jordan.app.utils.JordanHelper.extractActiveTasksInfo;

public class ClientAdapter extends ArrayAdapter<JordanClientDTO> implements StickyListHeadersAdapter, JordanGetClientsCallback, JordanConstant {

    private static final String TAG = "ClientAdapter";
    private static final ClientComparator CLIENT_COMPARATOR = new ClientComparator();
    /*package*/ static final String DEFAULT_STATE = "DEFAULT_STATE";
    private final OnClientClickListener clickListener;
    private final ClientDeletionCallback deleteCallback;
    private JordanClientModel model;
    private final LayoutInflater mInflater;

    public ClientAdapter(Context ctx, JordanClientModel model, OnClientClickListener clickListener, ClientDeletionCallback deleteCallback) {
        super(ctx, 0);
        this.model = model;
        mInflater = LayoutInflater.from(ctx);
        this.clickListener = clickListener;
        this.deleteCallback = deleteCallback;
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
        view.setOnLongClickListener(v -> displayClientOptions(v, client));
        return view;
    }

    private boolean displayClientOptions(View view, JordanClientDTO client) {
        PopupMenu popup = new PopupMenu(getContext(), view, Gravity.END);
        popup.getMenuInflater().inflate(R.menu.client_popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.infos_client) {
                    showInfosDialog(client);
                } else if (itemId == R.id.delete_client) {
                    showDeleteDialog(client);
                } else {
                    Log.e(TAG, "Unhandled menu item " + item.getTitle());
                }
                return true;
            }
        });
        popup.show();
        return true;
    }

    private void showDeleteDialog(JordanClientDTO client) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getContext().getString(R.string.delete_client_confirmation_dialog, client.getName()))
                .setMessage(R.string.delete_client_confirmation_dialog_message)
                .setPositiveButton(R.string.delete_client_confirmation_positive, (d, w) -> confirmDeleteClient(client))
                .setNegativeButton(R.string.delete_client_confirmation_negative, null)
                .show();

    }

    private void confirmDeleteClient(JordanClientDTO client) {
        model.delete(client, deleteCallback);
    }

    private void showInfosDialog(JordanClientDTO client) {
        List<String> details = Lists.newArrayList(
                getContext().getString(R.string.client_details_id, client.getClientId()),
                getContext().getString(R.string.client_details_state, client.getState()));

        if(CollectionUtils.isEmpty(client.getTasks())) {
            details.add(getContext().getString(R.string.client_details_no_task));
        } else {
            details.add(getContext().getString(R.string.client_details_task_count, client.getTasks().size()));
            for(JordanTaskDTO task : client.getTasks()){ // recursively print grand-child tasks ?
                details.add(getContext().getString(R.string.client_details_task_details,
                        task.getTaskId(), task.getName(), task.getState()));
            }
        }
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(client.getName())
                .setItems(details.toArray(new String[]{}), (dialog, which) -> {
                })
                .show();

    }

    public void refresh(JordanGetClientsCallback callback) {
        model.listClients(callback, this);
    }

    @Override
    public void onClientsLoaded(JordanClientDTO[] clients) {
        Arrays.sort(clients, CLIENT_COMPARATOR);
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
        String state = getItem(position).getState();
        return state == null ? DEFAULT_STATE : state;
    }
}
