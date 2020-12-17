package com.mara.jordan.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.MessagesStateAdapter;
import com.mara.jordan.app.model.dummy.MockDatabase;

public class MessagesStateFragment extends Fragment {


    private SwipeRefreshLayout messagesListRefreshLayout;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public MessagesStateFragment() {
    }

    public static Fragment newInstance() {
        MessagesStateFragment fragment = new MessagesStateFragment();
//        Bundle args = new Bundle();
//        args.putString(client_id);
//        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.message_state_view, container, false);
        setHasOptionsMenu(true);
        messagesListRefreshLayout = view.findViewById(R.id.swipe_refresh_message_state);
        messagesListRefreshLayout.setOnRefreshListener(this::refreshMessages);

        ListView statusList = (ListView) view.findViewById(R.id.message_state_list);
        statusList.setAdapter(new MessagesStateAdapter(getContext(), MockDatabase.MESSAGES));

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.messages_state_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.refresh_messages_state:
                messagesListRefreshLayout.setRefreshing(true);
                refreshMessages();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void refreshMessages() {
        //start async refresh clients
        Snackbar.make(getView(), "Refreshing messages...", Snackbar.LENGTH_SHORT).show();
        messagesListRefreshLayout.setRefreshing(false);
    }

}
