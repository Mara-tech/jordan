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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.MessagesStateAdapter;
import com.mara.jordan.app.api.JordanReadMessagesCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.app.model.dto.JordanMessageStateDTO;

public class MessagesStateFragment extends Fragment implements JordanReadMessagesCallback {


    private SwipeRefreshLayout messagesListRefreshLayout;
    private JordanTaskModel model;
    private MessagesStateAdapter messageStateAdapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public MessagesStateFragment() {
    }

    public static Fragment newInstance(JordanTaskModel model) {
        MessagesStateFragment fragment = new MessagesStateFragment();
//        Bundle args = new Bundle();
//        args.putString(client_id);
//        fragment.setArguments(args);
        fragment.model = model;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        messageStateAdapter = new MessagesStateAdapter(getContext(), model);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.message_state_view, container, false);
        setHasOptionsMenu(true);
        messagesListRefreshLayout = view.findViewById(R.id.swipe_refresh_message_state);
        messagesListRefreshLayout.setOnRefreshListener(this::refreshMessages);

        ListView statusList = view.findViewById(R.id.message_state_list);
        statusList.setAdapter(messageStateAdapter);

        refreshMessages();

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
                refreshMessages();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void refreshMessages() {
        //start async refresh clients
        if(!messagesListRefreshLayout.isRefreshing()){
            messagesListRefreshLayout.setRefreshing(true);
        }
        messageStateAdapter.refresh(this);
    }

    @Override
    public void onMessagesLoaded(JordanMessageStateDTO[] messages) {
        messagesListRefreshLayout.setRefreshing(false);
        if(messages.length == 0){
            Snackbar.make(getView(), R.string.no_message_state_to_display, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMessagesLoadingError(String errorMessage) {
        messagesListRefreshLayout.setRefreshing(false);
        Snackbar.make(getView(), R.string.message_state_refresh_failure, Snackbar.LENGTH_LONG)
                .setAction(R.string.message_state_refresh_failure_details, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new MaterialAlertDialogBuilder(getContext())
                                .setTitle(R.string.message_state_refresh_failure_details_dialog)
                                .setItems(new String[]{errorMessage}, null)
                                .show();
                    }
                })
                .show();
    }
}
