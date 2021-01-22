package com.mara.jordan.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.JordanTaskModel;

/**
 * A fragment representing a list of Items.
 */
public class ClientInteractionsFragment extends Fragment {

    private Fragment currentFragment = null;
    private JordanTaskModel model;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ClientInteractionsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        model = new JordanTaskModel(getContext(), getArguments().getLong(JordanTaskModel.CLIENT_ID, -1L));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getArguments() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getArguments().getString(JordanClientModel.CLIENT_NAME, getString(R.string.tasks_fragment_default_title)));
        }
        else {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("ERROR : No client selected");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.client_interactions_layout, container, false);

        BottomNavigationView bottomMenu = (BottomNavigationView)view.findViewById(R.id.bottom_navigation);
        BottomNavigationView.OnNavigationItemSelectedListener navigationItemSelectedListener =
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.client_interaction_status:
                                openFragment(ReadStatusFragment.newInstance(model));
                                return true;
                            case R.id.client_interaction_action:
                                openFragment(TaskAndActionsFragment.newInstance(model));
                                return true;
                            case R.id.client_interaction_messages_state:
                                openFragment(MessagesStateFragment.newInstance(model));
                                return true;
                        }
                        return false;
                    }
                };
        bottomMenu.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
        bottomMenu.setSelectedItemId(R.id.client_interaction_action);
        return view;
    }


    public void openFragment(Fragment fragment) {
        currentFragment = fragment;
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.client_inner_host_fragment, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }


}