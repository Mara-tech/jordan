package com.mara.jordan.app.ui;

import android.content.DialogInterface;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.MessageFilterAuthorAdapter;
import com.mara.jordan.app.adapter.MessageFilterStateAdapter;
import com.mara.jordan.app.adapter.MessageFilterTaskAdapter;
import com.mara.jordan.app.adapter.MessagesStateAdapter;
import com.mara.jordan.app.api.JordanReadMessagesCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.core.dto.JordanMessageStateDTO;

import java.util.Map;

public class MessagesStateFragment extends Fragment implements JordanReadMessagesCallback {


    private SwipeRefreshLayout messagesListRefreshLayout;
    private JordanTaskModel model;
    private MessagesStateAdapter messageStateAdapter;
    private MessageFilterTaskAdapter messageFilterTaskAdapter;
    private MessageFilterAuthorAdapter messageFilterAuthorAdapter;
    private MessageFilterStateAdapter messageFilterStateAdapter;
    private Map<String, Boolean> taskFilter;
    private Map<String, Boolean> authorFilter;
    private Map<String, Boolean> stateFilter;

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
        messageFilterTaskAdapter = new MessageFilterTaskAdapter(getContext());
        messageFilterAuthorAdapter = new MessageFilterAuthorAdapter(getContext());
        messageFilterStateAdapter = new MessageFilterStateAdapter(getContext());
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

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshMessages();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.messages_state_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.refresh_messages_state) {
            refreshMessages();
            return true;
        } else if (itemId == R.id.filter_message) {
            filterMessageDialog();
            return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }


    /**
     * Show dialog with task+author+state checkboxes
     */
    private void filterMessageDialog() {

        messageFilterTaskAdapter.resetTempState();
        messageFilterAuthorAdapter.resetTempState();
        messageFilterStateAdapter.resetTempState();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View filterDialogView = requireActivity().getLayoutInflater().inflate(R.layout.message_filter_dialog, null);
        ListView taskList = filterDialogView.findViewById(R.id.message_filter_task_list);
        taskList.setAdapter(messageFilterTaskAdapter);
        ListView authorList = filterDialogView.findViewById(R.id.message_filter_author_list);
        authorList.setAdapter(messageFilterAuthorAdapter);
        ListView stateList = filterDialogView.findViewById(R.id.message_filter_state_list);
        stateList.setAdapter(messageFilterStateAdapter);

        builder.setView(filterDialogView)
                .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        messageFilterTaskAdapter.applyTempView();
                        messageFilterAuthorAdapter.applyTempView();
                        messageFilterStateAdapter.applyTempView();
                        setMessageFilters(messageFilterTaskAdapter.getFilterMapping(),
                                messageFilterAuthorAdapter.getFilterMapping(),
                                messageFilterStateAdapter.getFilterMapping());
                    }
                })
                .setNeutralButton(R.string.cancel, (dialog, which) -> {})

                .show();

    }

    private void setMessageFilters(Map<String, Boolean> taskFilter, Map<String, Boolean> authorFilter, Map<String, Boolean> stateFilter) {
        this.taskFilter = taskFilter;
        this.authorFilter = authorFilter;
        this.stateFilter = stateFilter;
        updateMessageAdapter();
    }

    private void updateMessageAdapter() {
        messageStateAdapter.select(taskFilter, authorFilter, stateFilter);
    }


    private void refreshMessages() {
        if(!messagesListRefreshLayout.isRefreshing()){
            messagesListRefreshLayout.setRefreshing(true);
        }
        messageStateAdapter.refresh(this, taskFilter, authorFilter, stateFilter);
    }

    @Override
    public void onMessagesLoaded(JordanMessageStateDTO[] messages) {
        messagesListRefreshLayout.setRefreshing(false);
        messageFilterTaskAdapter.onItemsLoaded(messages);
        messageFilterAuthorAdapter.onItemsLoaded(messages);
        messageFilterStateAdapter.onItemsLoaded(messages);
        if(messages.length == 0){
            if(getView() != null){
                Snackbar.make(getView(), R.string.no_message_state_to_display, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onMessagesLoadingError(String errorMessage) {
        messagesListRefreshLayout.setRefreshing(false);
        if(getView() != null && getContext() != null) {
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
}
