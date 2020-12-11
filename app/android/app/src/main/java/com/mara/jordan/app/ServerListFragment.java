package com.mara.jordan.app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.ListFragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;


import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class ServerListFragment extends ListFragment implements AdapterView.OnItemClickListener {

    public static final String[] SERVERS = {"IA Training Perso", "IA Training boulot", "Communauté NextGen"};
    private ListAdapter serverListAdapter;
    private SwipeRefreshLayout serverListRefreshLayout;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.server_list_view, container, false);

        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.main_activity_title);

        serverListRefreshLayout = view.findViewById(R.id.swipe_refresh_server);

        serverListAdapter = new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1, SERVERS);


        FloatingActionButton fab = view.findViewById(R.id.add_server);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Show dialog : Add server", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        // Inflate the layout for this fragment
        return view;
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setListAdapter(serverListAdapter);
        getListView().setOnItemClickListener(this);
        serverListRefreshLayout.setOnRefreshListener(this::refreshServer);

//        view.findViewById(R.id.button_first).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                NavHostFragment.findNavController(ServerListFragment.this)
//                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
//            }
//        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Bundle selectedServerBundle = new Bundle();
        selectedServerBundle.putString("server_name", SERVERS[position]);
            NavHostFragment.findNavController(ServerListFragment.this)
                .navigate(R.id.action_server_to_client, selectedServerBundle);
    }

    private void refreshServer() {
        //start async refresh clients
        Snackbar.make(getView(), "Refreshing servers...", Snackbar.LENGTH_SHORT);
    }
}