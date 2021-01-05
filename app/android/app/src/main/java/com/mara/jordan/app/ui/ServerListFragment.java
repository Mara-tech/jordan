package com.mara.jordan.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.ListFragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.JordanServerModel;

public class ServerListFragment extends ListFragment implements AdapterView.OnItemClickListener {

    public static final String[] SERVERS = {"\uD83D\uDE0A IA Training Perso", "\uD83D\uDCBC IA Training boulot", "\uD83D\uDE80 Communauté NextGen"};
    private ListAdapter serverListAdapter;
    private SwipeRefreshLayout serverListRefreshLayout;
    private JordanServerModel model;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        model = new JordanServerModel(getContext());


        final View view = inflater.inflate(R.layout.server_list_view, container, false);
        setHasOptionsMenu(true);

        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.main_activity_title);

        serverListRefreshLayout = view.findViewById(R.id.swipe_refresh_server);

        //TODO custom adapter. What to show ? (need api calls, so fill adapter with async callback)
        //TODO plus popupwindow for Edit/Delete
        serverListAdapter = new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1, SERVERS);


        FloatingActionButton fab = view.findViewById(R.id.add_server);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addServerDialog();
            }
        });

        // Inflate the layout for this fragment
        return view;
    }

    private void addServerDialog() {
        //TODO make own dialog fragment for
        // 1- make text field mandatory (disable Positive Button if not valid
        // 2- Positive Button PHASE_1 = "test" (call /api/hello), and if valid, set positive button to PHASE_2 : "add server"
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View addServerDialogView = requireActivity().getLayoutInflater().inflate(R.layout.add_server_dialog, null);
        CheckBox rememberLoginCb = addServerDialogView.findViewById(R.id.add_server_dialog_remember_login);
        EditText loginField = addServerDialogView.findViewById(R.id.add_server_dialog_login);
        CheckBox rememberPasswordCb = addServerDialogView.findViewById(R.id.add_server_dialog_remember_password);
        EditText passwordField = addServerDialogView.findViewById(R.id.add_server_dialog_password);
        rememberLoginCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                loginField.setEnabled(isChecked);
                rememberPasswordCb.setEnabled(isChecked);
                passwordField.setEnabled(isChecked);
            }
        });
        rememberPasswordCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                passwordField.setEnabled(isChecked);
            }
        });

        builder
                .setTitle(R.string.add_server_dialog_title)
                .setView(addServerDialogView)
                .setPositiveButton(R.string.add_server_dialog_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        addServer(addServerDialogView);
                    }
                })
                .setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Do nothing
                    }
                })
                .show();

    }

    private void addServer(View dialogView) {
        EditText serverNameField = dialogView.findViewById(R.id.add_server_dialog_server_name);
        String serverName = serverNameField.getText().toString();

        EditText serverBaseUriField = dialogView.findViewById(R.id.add_server_dialog_server_base_uri);
        String serverBaseUri = serverBaseUriField.getText().toString();

        EditText loginField = dialogView.findViewById(R.id.add_server_dialog_login);
        String login = loginField.getText().toString();

        EditText passwordField = dialogView.findViewById(R.id.add_server_dialog_password);
        String password = serverNameField.getText().toString();

        CheckBox rememberLoginField = dialogView.findViewById(R.id.add_server_dialog_remember_login);
        boolean rememberLogin = rememberLoginField.isChecked();

        CheckBox rememberPasswordField = dialogView.findViewById(R.id.add_server_dialog_remember_password);
        boolean rememberPassword = rememberLogin && rememberPasswordField.isChecked();

        model.addServer(serverName, serverBaseUri, rememberLogin, login, rememberPassword, password);
        //TODO invalidate adapter
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setListAdapter(serverListAdapter);
        getListView().setOnItemClickListener(this);
        serverListRefreshLayout.setOnRefreshListener(this::refreshServers);

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

    private void refreshServers() {
        //start async refresh clients
        Snackbar.make(getView(), "Refreshing servers...", Snackbar.LENGTH_SHORT).show();
        serverListRefreshLayout.setRefreshing(false);
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
                serverListRefreshLayout.setRefreshing(true);
                refreshServers();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

}