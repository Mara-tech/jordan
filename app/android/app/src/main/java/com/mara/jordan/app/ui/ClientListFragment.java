package com.mara.jordan.app.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.ClientAdapter;
import com.mara.jordan.app.api.JordanGetClientsCallback;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.JordanServerModel;
import com.mara.jordan.app.model.dto.JordanClientDTO;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;

public class ClientListFragment extends InServerFragment implements OnClientClickListener, JordanGetClientsCallback, ClientDeletionCallback {

    private ClientAdapter adapter;
    private SwipeRefreshLayout clientListRefreshLayout;
    private JordanClientModel model;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        long serverId = getArguments().getLong(JordanServerModel.SERVER_ID);
        String serverBaseUrl = getArguments().getString(JordanServerModel.SERVER_BASE_URL);
        model = new JordanClientModel(getContext(), serverBaseUrl);
        adapter = new ClientAdapter(getContext(), model, this, this);
    }


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.client_list_view, container, false);
        setHasOptionsMenu(true);

        clientListRefreshLayout = view.findViewById(R.id.swipe_refresh_client);
        clientListRefreshLayout.setOnRefreshListener(this::refreshClients);

        StickyListHeadersListView stickyList = view.findViewById(R.id.client_list);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stickyList.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        stickyList.setAdapter(adapter);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshClients();
    }

    private void refreshClients() {
        if(!clientListRefreshLayout.isRefreshing()){
            clientListRefreshLayout.setRefreshing(true);
        }
        adapter.refresh(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getArguments() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getArguments().getString(JordanClientModel.SERVER_NAME, getString(R.string.clients_fragment_default_title)));
        }
        else {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("ERROR : No server selected");
        }
    }

    @Override
    public void onClientClicked(JordanClientDTO selectedClient) {
        final Bundle selectedClientBundle = new Bundle();
        selectedClientBundle.putLong(JordanClientModel.CLIENT_ID, selectedClient.getClientId());
        selectedClientBundle.putString(JordanClientModel.CLIENT_NAME, selectedClient.getName());
        NavHostFragment.findNavController(ClientListFragment.this)
                .navigate(R.id.action_client_to_task, selectedClientBundle);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.client_list_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_clients) {
            refreshClients();
            return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    @Override
    protected JordanClientModel getModel() {
        return model;
    }

    @Override
    public void onClientsLoaded(JordanClientDTO[] clients) {
        clientListRefreshLayout.setRefreshing(false);
        if(clients.length == 0){
            if(getView() != null){
                Snackbar.make(getView(), R.string.no_client_to_display, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onClientsLoadingError(String errorMessage) {
        clientListRefreshLayout.setRefreshing(false);
        if(getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.client_refresh_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.client_refresh_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.client_refresh_failure_details_dialog)
                                    .setItems(new String[]{errorMessage}, null)
                                    .show();
                        }
                    })
                    .show();
        }
    }

    @Override
    public void onClientDeletionError(String errorMessage) {
        if(getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.client_delete_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.client_delete_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.client_delete_failure_details_dialog)
                                    .setItems(new String[]{errorMessage}, null)
                                    .show();
                        }
                    })
                    .show();
        }

    }

    @Override
    public void onClientDeleted() {
        refreshClients();
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(),
                    R.string.client_deleted,
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBaseDeleted() {
        super.onBaseDeleted();
        refreshClients();
    }
}