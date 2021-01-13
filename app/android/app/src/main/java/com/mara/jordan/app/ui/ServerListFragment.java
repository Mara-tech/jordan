package com.mara.jordan.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.ImmutableList;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.ServerAdapter;
import com.mara.jordan.app.db.JordanListServersCallback;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.db.OnServerUpdateListener;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.JordanServerModel;

import java.util.List;

public class ServerListFragment extends Fragment implements JordanListServersCallback,
        OnServerClickListener,
        OnServerUpdateListener,
        ServerAddOrUpdateCallback,
        ServerImportExportCallback {

    private ServerAdapter serverListAdapter;
    private SwipeRefreshLayout serverListRefreshLayout;
    private JordanServerModel model;
    private List<JordanServer> serversList = ImmutableList.of();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        this.model = new JordanServerModel(getContext());
        serverListAdapter = new ServerAdapter(getContext(), model, this, this);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.server_list_view, container, false);
        serverListRefreshLayout = view.findViewById(R.id.swipe_refresh_server);
        serverListRefreshLayout.setOnRefreshListener(this::refreshServers);

        ListView list = view.findViewById(R.id.server_list);
        list.setAdapter(serverListAdapter);

        FloatingActionButton fab = view.findViewById(R.id.add_server);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddServerDialog();
            }
        });

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshServers();
    }

    @Override
    public void onResume() {
        super.onResume();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.main_activity_title);
    }

    private void showAddServerDialog() {
        showAddServerDialog(null);
    }

    private void showAddServerDialog(@Nullable JordanServer prefillServer) {
        final AddServerDialog dialog = new AddServerDialog();
        dialog.setAddServerDialogListener(this);
        dialog.setModel(model);
        if(prefillServer != null){
            dialog.setServerToBeUpdated(prefillServer);
        }
        dialog.show(getChildFragmentManager(), "addServerDialog");
    }

    @Override
    public void addServer(JordanServer jordanServer) {
        model.addServer(jordanServer, this);
    }

    @Override
    public void updateServer(JordanServer entity) {
        model.update(entity, this);
    }

    @Override
    public void onServerClicked(JordanServer selectedServer) {
        final Bundle selectedServerBundle = new Bundle();
        selectedServerBundle.putLong(JordanClientModel.SERVER_ID, selectedServer.getId());
        selectedServerBundle.putString(JordanClientModel.SERVER_BASE_URL, selectedServer.getUrl());
        selectedServerBundle.putString(JordanClientModel.SERVER_NAME, selectedServer.getName());
        NavHostFragment.findNavController(ServerListFragment.this)
                .navigate(R.id.action_server_to_client, selectedServerBundle);
    }

    @Override
    public void onServerToBeUpdated(JordanServer selectedServer) {
        showAddServerDialog(selectedServer);
    }

    private void refreshServers() {
        if(!serverListRefreshLayout.isRefreshing()){
            serverListRefreshLayout.setRefreshing(true);
        }
        serverListAdapter.refresh(this);
    }

    @Override
    public void onServersLoaded(List<JordanServer> servers) {
        serverListRefreshLayout.setRefreshing(false);
        serversList = servers;
        if(servers.isEmpty()){
            if(getView() != null){
                Snackbar.make(getView(), R.string.no_server_to_display, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onServersLoadingError(Throwable error) {
        serverListRefreshLayout.setRefreshing(false);
        if(getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.server_refresh_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.server_refresh_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.server_refresh_failure_details_dialog)
                                    .setItems(new String[]{
                                            error.toString(),
                                            error.getLocalizedMessage() != null ? error.getLocalizedMessage() : error.getMessage()
                                    }, null)
                                    .show();
                        }
                    })
                    .show();
        }
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.server_list_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.refresh_server:
                refreshServers();
                return true;

            case R.id.import_server_list:
                importDialog();
                return true;
            case R.id.export_server_list:
                exportServersClicked();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void exportServersClicked() {
        if(serversList.isEmpty()){
            if(getView() != null && getContext() != null) {
                Snackbar.make(getView(), R.string.no_server_to_export,
                        Snackbar.LENGTH_SHORT).show();
            }
        } else {
            exportDialog(serversList);
        }
    }
  private void exportDialog(List<JordanServer> serversList) {
        ExportServerDialog dialog = new ExportServerDialog();
        dialog.setItems(serversList);
        dialog.setCallback(this);
        dialog.show(getChildFragmentManager(), "exportServerDialog");
    }

    private void importDialog() {
        ImportServerDialog dialog = new ImportServerDialog();
        dialog.setCallback(this);
        dialog.show(getChildFragmentManager(), "importServerDialog");
    }

    @Override
    public void onServerAdded(JordanServer[] entities) {
        refreshServers();
        if(getView() != null && getContext() != null && entities != null && entities.length > 0) {
            String serverAddedMessage = entities.length == 1 ?
                    getContext().getString(R.string.server_added, entities[0].getName())
                    : getContext().getString(R.string.server_multi_added, entities.length);
            Snackbar.make(getView(),
                    serverAddedMessage,
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServerUpdated(JordanServer entity) {
        refreshServers();
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(),
                    getContext().getString(R.string.server_updated, entity.getName()),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServerDeleted(JordanServer entity) {
        refreshServers();
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(),
                    getContext().getString(R.string.server_deleted, entity.getName()),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServerUpdateError(Throwable error, JordanServer... entities) {

    }

    @Override
    public void onServerExported(int count) {
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(),
                    getContext().getString(R.string.server_exported_clipboard, count),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServerImportParsed(JordanServer[] servers) {
        model.addServers(servers, this);
    }

    @Override
    public void onServerImportError(String... errorMessages) {
        if(getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.server_import_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.server_import_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.server_import_failure_details_dialog)
                                    .setItems(errorMessages, null)
                                    .show();
                        }
                    })
                    .show();
        }
    }
}