package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanGetClientsCallback;
import com.mara.jordan.app.db.JordanListServersCallback;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.db.OnServerUpdateListener;
import com.mara.jordan.app.model.JordanServerModel;
import com.mara.jordan.core.dto.JordanClientDTO;
import com.mara.jordan.app.ui.OnServerClickListener;

import java.util.List;

import static com.mara.jordan.core.JordanHelper.NO_TASK_WITH_PROGRESS_FLAG;
import static com.mara.jordan.core.JordanHelper.estimateClientProgress;
import static com.mara.jordan.core.JordanHelper.getActiveClientCount;
import static com.mara.jordan.core.JordanHelper.getActiveTaskCount;

public class ServerAdapter extends ArrayAdapter<JordanServer> implements JordanListServersCallback {

    private static final String TAG = "ServerAdapter";
    private final JordanServerModel model;
    private final OnServerClickListener clickListener;
    private final OnServerUpdateListener updateListener;
    private final LayoutInflater mInflater;

    public ServerAdapter(Context ctx, JordanServerModel model, OnServerClickListener clickListener, OnServerUpdateListener updateListener) {
        super(ctx, 0);
        this.model = model;
        mInflater = LayoutInflater.from(ctx);
        this.clickListener = clickListener;
        this.updateListener = updateListener;
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = mInflater.inflate(R.layout.server_item_layout, parent, false);

        JordanServer server = getItem(position);
        TextView serverName = view.findViewById(R.id.server_name);
        serverName.setText(server.getName());

        TextView activeClient = view.findViewById(R.id.server_active_clients);
        TextView serverDetails = view.findViewById(R.id.server_details);
        activeClient.setText("");
        serverDetails.setText("");
        String[] activeClientStates = getContext().getResources().getStringArray(R.array.active_client_states);
        String[] activeTaskStates = getContext().getResources().getStringArray(R.array.active_task_states);
        model.getServerInfo(server, new JordanGetClientsCallback() {
            @Override
            public void onClientsLoaded(JordanClientDTO[] clients) {
                displayActiveClients(activeClient, clients, activeClientStates);
                displayServerDetails(serverDetails, clients, activeClientStates, activeTaskStates);
            }

            @Override
            public void onClientsLoadingError(String errorMessage) {
                Log.w(TAG, "Cannot extract data from server " + server.getName() + " : " + server.getUrl(), new Throwable(errorMessage));
                displayCannotConnect(activeClient, serverDetails, errorMessage);
            }
        });

        view.setOnClickListener(v -> clickListener.onServerClicked(server));
        view.setOnLongClickListener(v -> displayServerOptions(v, server));
        return view;
    }

    private void displayCannotConnect(TextView activeClient, TextView serverDetails, String errorMessage) {
        activeClient.setText(R.string.server_cannot_connect);
        serverDetails.setText(errorMessage);
    }

    private void displayActiveClients(TextView activeClient, JordanClientDTO[] clients, String[] activeClientStates) {
        String activeClientText;
        long activeClientCount = getActiveClientCount(clients, activeClientStates);
        boolean hasActiveClients = activeClientCount > 0;
        if(!hasActiveClients){
            activeClientText = getContext().getString(R.string.server_no_active_client);
        } else {
            activeClientText = getContext().getString(R.string.server_active_clients, activeClientCount);
        }
        activeClient.setText(activeClientText);
    }

    private void displayServerDetails(TextView serverDetails, JordanClientDTO[] clients, String[] activeClientStates, String[] activeTaskStates) {
        StringBuilder detailsText = new StringBuilder();
        long activeClientCount = getActiveClientCount(clients, activeClientStates);
        boolean hasActiveClients = activeClientCount > 0;

        if(!hasActiveClients){
//            detailsText.append();
        } else {
            long activeTaskCount = getActiveTaskCount(clients, activeTaskStates);
            boolean hasActiveTaskCount = activeTaskCount > 0;
            if(hasActiveTaskCount){
                if(activeTaskCount == 1){
                    detailsText.append(getContext().getString(R.string.server_active_task_single));
                }else {
                    detailsText.append(getContext().getString(R.string.server_active_tasks, activeTaskCount));
                }
                int progress = estimateClientProgress(clients, activeTaskStates);
                boolean hasProgress = progress != NO_TASK_WITH_PROGRESS_FLAG;
                if(hasProgress){
                    detailsText.append(getContext().getString(R.string.server_active_tasks_progress, progress));
                }
            }
        }
        serverDetails.setText(detailsText.toString());
    }

    private boolean displayServerOptions(View view, JordanServer server) {
        PopupMenu popup = new PopupMenu(getContext(), view, Gravity.END);
        popup.getMenuInflater().inflate(R.menu.server_popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.update_server) {
                    showUpdateDialog(server);
                } else if (itemId == R.id.delete_server) {
                    showDeleteDialog(server);
                } else {
                    Log.e(TAG, "Unhandled menu item " + item.getTitle());
                }
                return true;
            }
        });
        popup.show();
        return true;
    }

    private void showUpdateDialog(JordanServer serverToBeUpdated) {
        clickListener.onServerToBeUpdated(serverToBeUpdated);
    }

    private void showDeleteDialog(JordanServer server) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getContext().getString(R.string.delete_server_confirmation_dialog, server.getName()))
                .setPositiveButton(R.string.delete_server_confirmation_positive, (d, w) -> confirmDeleteServer(server))
                .setNegativeButton(R.string.delete_server_confirmation_negative, null)
                .show();
    }

    private void confirmDeleteServer(JordanServer server) {
        model.delete(server, updateListener);
    }

    public void refresh(JordanListServersCallback callback) {
        model.listServers(callback, this);
    }

    @Override
    public void onServersLoaded(List<JordanServer> servers) {
        display(servers);
    }

    private void display(List<JordanServer> servers) {
        clear();
        addAll(servers);
    }

    @Override
    public void onServersLoadingError(Throwable error) {
        Log.e(TAG, "Error while loading servers", error);
    }
}
