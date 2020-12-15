package com.mara.jordan.app.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.TaskAndActionsAdapter;
import com.mara.jordan.app.model.dummy.MockDatabase;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;

public class TaskAndActionsFragment extends Fragment {


    private SwipeRefreshLayout tasksListRefreshLayout;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TaskAndActionsFragment() {
    }

    public static Fragment newInstance() {
        TaskAndActionsFragment fragment = new TaskAndActionsFragment();
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
        View view = inflater.inflate(R.layout.task_and_actions_list_view, container, false);
        setHasOptionsMenu(true);

        tasksListRefreshLayout = view.findViewById(R.id.swipe_refresh_tasks);
        tasksListRefreshLayout.setOnRefreshListener(this::refreshTasks);

        StickyListHeadersListView stickyList = (StickyListHeadersListView) view.findViewById(R.id.task_list);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stickyList.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        TaskAndActionsAdapter adapter = new TaskAndActionsAdapter(getContext(), MockDatabase.ACTIONS);
        stickyList.setAdapter(adapter);



        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.tasks_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.refresh_tasks:
                tasksListRefreshLayout.setRefreshing(true);
                refreshTasks();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void refreshTasks() {
        //start async refresh clients
        Snackbar.make(getView(), "Refreshing tasks...", Snackbar.LENGTH_SHORT).show();
        tasksListRefreshLayout.setRefreshing(false);
    }

}
