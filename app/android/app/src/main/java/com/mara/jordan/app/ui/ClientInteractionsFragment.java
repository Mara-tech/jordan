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
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.JordanTaskModel;

/**
 * A fragment representing a list of Items.
 */
public class ClientInteractionsFragment extends InServerFragment {

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
    }

    /**
     * The model the four tabs share, created on first use : after a rotation, the tab restored by
     * the child fragment manager asks for it from {@code super.onCreate}, before the rest of
     * {@link #onCreate} has run.
     */
    public JordanTaskModel getTaskModel() {
        if (model == null) {
            model = new JordanTaskModel(getContext(), getArguments().getLong(JordanTaskModel.CLIENT_ID, -1L));
        }
        return model;
    }

    /**
     * The model of the tab a fragment of this screen is : its parent is always this screen.
     */
    static JordanTaskModel taskModelOf(Fragment tab) {
        return ((ClientInteractionsFragment) tab.requireParentFragment()).getTaskModel();
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
                        int itemId = item.getItemId();
                        if (itemId == R.id.client_interaction_status) {
                            openFragment(ReadStatusFragment.newInstance());
                            return true;
                        } else if (itemId == R.id.client_interaction_action) {
                            openFragment(TaskAndActionsFragment.newInstance());
                            return true;
                        } else if (itemId == R.id.client_interaction_messages_state) {
                            openFragment(MessagesStateFragment.newInstance());
                            return true;
                        } else if (itemId == R.id.client_interaction_metrics) {
                            openFragment(MetricsFragment.newInstance());
                            return true;
                        }
                        return false;
                    }
                };
        bottomMenu.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
        return view;
    }

    /**
     * The tab displayed is the source of truth, restored by the child fragment manager after a
     * rotation : a first display opens the Actions tab, a later one only checks the item of the tab
     * already there. Done here rather than in {@code onCreateView}, so that the menu's own restored
     * state cannot contradict it.
     */
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        BottomNavigationView bottomMenu = requireView().findViewById(R.id.bottom_navigation);
        Fragment displayed = getDisplayedTab();
        if (displayed == null) {
            bottomMenu.setSelectedItemId(R.id.client_interaction_action);
        } else {
            bottomMenu.getMenu().findItem(menuItemOf(displayed)).setChecked(true);
        }
    }

    static int menuItemOf(Fragment tab) {
        if (tab instanceof ReadStatusFragment) {
            return R.id.client_interaction_status;
        } else if (tab instanceof MessagesStateFragment) {
            return R.id.client_interaction_messages_state;
        } else if (tab instanceof MetricsFragment) {
            return R.id.client_interaction_metrics;
        }
        return R.id.client_interaction_action;
    }

    /**
     * The tabs belong to this screen's child fragment manager, which saves and restores them with
     * it. Switching tabs is not a navigation step : it is kept out of the back stack, where popping
     * it would show one tab under the item of another.
     */
    public void openFragment(Fragment fragment) {
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.client_inner_host_fragment, fragment)
                .commit();
    }

    @Nullable
    Fragment getDisplayedTab() {
        return getChildFragmentManager().findFragmentById(R.id.client_inner_host_fragment);
    }

    @Override
    protected JordanClientModel getModel() {
        return getTaskModel();
    }

    /**
     * The calls are made by the tab currently displayed, so it is the one to reload.
     */
    @Override
    public void refreshContent() {
        if (!isAdded()) {
            return;
        }
        Fragment displayed = getDisplayedTab();
        if (displayed instanceof JordanRefreshable && displayed.getView() != null) {
            ((JordanRefreshable) displayed).refreshContent();
        }
    }

    @Override
    public void onBaseDeleted() {
        super.onBaseDeleted();
        NavHostFragment.findNavController(ClientInteractionsFragment.this).popBackStack();
    }
}