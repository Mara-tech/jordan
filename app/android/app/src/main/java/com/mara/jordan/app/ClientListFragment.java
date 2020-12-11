package com.mara.jordan.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.ListFragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class ClientListFragment extends ListFragment implements AdapterView.OnItemClickListener {

    private static final String[] CLIENTS = new String[]{"Musique classique evaluation", "Musique Final Fantasy training", "Musique classique training", "Musique Nintendo evaluation"};
    private ArrayAdapter clientListAdapter;
    private SwipeRefreshLayout clientListRefreshLayout;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.client_list_view, container, false);
        clientListRefreshLayout = view.findViewById(R.id.swipe_refresh_client);

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        clientListAdapter = new ArrayAdapter(getActivity(), android.R.layout.simple_list_item_1, CLIENTS);
        setListAdapter(clientListAdapter);

        clientListRefreshLayout.setOnRefreshListener(this::refreshClients);

        getListView().setOnItemClickListener(this);
    }

    private void refreshClients() {
        //start async refresh clients
        Snackbar.make(getView(), "Refreshing clients...", Snackbar.LENGTH_SHORT);
    }

    private void onRefreshComplete(List<String> result) {
        clientListAdapter.clear();
        for (String client : result) {
            clientListAdapter.add(client);
        }
        clientListRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getArguments() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getArguments().getString("server_name", getString(R.string.clients_fragment_default_title)));
        }
        else {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("ERROR : No server selected");
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        NavHostFragment.findNavController(ClientListFragment.this)
//                .navigate(R.id.action_client_to_server);
        final Bundle selectedclientBundle = new Bundle();
        selectedclientBundle.putString("client_name", CLIENTS[position]);
        NavHostFragment.findNavController(ClientListFragment.this)
                .navigate(R.id.action_client_to_task, selectedclientBundle);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_refresh:
                clientListRefreshLayout.setRefreshing(true);
                refreshClients();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }
}