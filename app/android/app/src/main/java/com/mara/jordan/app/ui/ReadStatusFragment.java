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
import com.mara.jordan.app.adapter.ReadStatusAdapter;
import com.mara.jordan.app.model.dummy.MockDatabase;

public class ReadStatusFragment extends Fragment {


    private SwipeRefreshLayout statusListRefreshLayout;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ReadStatusFragment() {
    }

    public static ReadStatusFragment newInstance() {
        ReadStatusFragment fragment = new ReadStatusFragment();
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
        View view = inflater.inflate(R.layout.read_status_view, container, false);
        setHasOptionsMenu(true);
        statusListRefreshLayout = view.findViewById(R.id.swipe_refresh_status);
        statusListRefreshLayout.setOnRefreshListener(this::refreshStatus);

        ListView statusList = (ListView) view.findViewById(R.id.read_status_list);
        statusList.setAdapter(new ReadStatusAdapter(getContext(), MockDatabase.STATUSES));

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.read_status_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.refresh_status:
                statusListRefreshLayout.setRefreshing(true);
                refreshStatus();
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void refreshStatus() {
        //start async refresh clients
        Snackbar.make(getView(), "Refreshing status...", Snackbar.LENGTH_SHORT).show();
        statusListRefreshLayout.setRefreshing(false);
    }
}
