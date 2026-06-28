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
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.TaskAndActionsAdapter;
import com.mara.jordan.app.api.JordanGetActionsCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.core.dto.JordanActionDefinitionWithTaskDTO;
import com.mara.jordan.core.dto.JordanActionParameterDTO;

import java.util.ArrayList;
import java.util.List;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;

public class TaskAndActionsFragment extends Fragment implements JordanGetActionsCallback, JordanSendMessageUiCallback {


    private SwipeRefreshLayout tasksListRefreshLayout;
    private JordanTaskModel model;
    private TaskAndActionsAdapter adapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TaskAndActionsFragment() {
    }

    public static Fragment newInstance(JordanTaskModel model) {
        TaskAndActionsFragment fragment = new TaskAndActionsFragment();
//        Bundle args = new Bundle();
//        args.putString(client_id);
//        fragment.setArguments(args);
        fragment.model = model;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new TaskAndActionsAdapter(getContext(), model, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.task_and_actions_list_view, container, false);
        setHasOptionsMenu(true);

        tasksListRefreshLayout = view.findViewById(R.id.swipe_refresh_tasks);
        tasksListRefreshLayout.setOnRefreshListener(this::refreshTasks);

        StickyListHeadersListView stickyList = view.findViewById(R.id.task_list);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stickyList.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        stickyList.setAdapter(adapter);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshTasks();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.tasks_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_tasks) {
            refreshTasks();
            return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void refreshTasks() {
        if(!tasksListRefreshLayout.isRefreshing()){
            tasksListRefreshLayout.setRefreshing(true);
        }
        adapter.refresh(this);
    }

    @Override
    public void onActionsLoaded(JordanActionDefinitionWithTaskDTO[] actions) {
        tasksListRefreshLayout.setRefreshing(false);
        if(actions.length == 0){
            if(getView() != null && getContext() != null){
                Snackbar.make(getView(), R.string.no_action_definitions_to_display, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActionsLoadingError(String errorMessage) {
        tasksListRefreshLayout.setRefreshing(false);
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(), R.string.action_definitions_refresh_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_definitions_refresh_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.action_definitions_refresh_failure_details_dialog)
                                    .setItems(new String[]{errorMessage}, null)
                                    .show();
                        }
                    })
                    .show();
        }
    }

    @Override
    public void alertMandatoryFieldMissing(List<JordanActionParameterDTO> missingInput) {
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(), R.string.message_not_sent_mandatory, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.message_not_sent_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.message_not_sent_mandatory_dialog)
                                    .setItems(renderMissingMandatoryFields(missingInput), null)
                                    .show();
                        }
                    })
                    .show();
        }
    }

    private String[] renderMissingMandatoryFields(List<JordanActionParameterDTO> missingInput) {
//        return missingInput.stream().map(param -> param.getName().concat(" (").concat(param.getType()).concat(")")).collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        for (JordanActionParameterDTO param : missingInput) {
            String concat = getString(R.string.action_missing_mandatory_field_detail, param.getName(), param.getType());
            list.add(concat);
        }
        return list.toArray(new String[]{});
    }

    @Override
    public void onMessageSent(long messageId) {
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(), R.string.message_sent, Snackbar.LENGTH_SHORT) //TODO add action name ?
                    .setAction(R.string.message_sent_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.message_sent)
                                    .setItems(new String[]{
                                            getString(R.string.message_sent_id, messageId)
                                    }, null)
                                    .show();
                        }
                    })
                    .show();
        }
    }

    @Override
    public void onMessageSendingError(String errorMessage) {
        if(getView() != null && getContext() != null){
            Snackbar.make(getView(), R.string.message_not_sent_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.message_not_sent_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.message_not_sent_failure_details_dialog)
                                    .setItems(new String[]{errorMessage}, null)
                                    .show();
                        }
                    })
                    .show();
        }
    }
}
