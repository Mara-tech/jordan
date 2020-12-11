package com.mara.jordan.app;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mara.jordan.app.dummy.MockDatabase;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;

/**
 * A fragment representing a list of Items.
 */
public class TaskAndActionsFragment extends Fragment {

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TaskAndActionsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getArguments() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getArguments().getString("client_name", getString(R.string.tasks_fragment_default_title)));
        }
        else {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("ERROR : No client selected");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.task_and_actions_list_view, container, false);


        StickyListHeadersListView stickyList = (StickyListHeadersListView) view.findViewById(R.id.task_list);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stickyList.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        TaskAndActionsAdapter adapter = new TaskAndActionsAdapter(getContext(), MockDatabase.ITEMS);
        stickyList.setAdapter(adapter);


//        // Set the adapter
//        if (view instanceof RecyclerView) {
//            Context context = view.getContext();
//            RecyclerView recyclerView = (RecyclerView) view;
//            recyclerView.setLayoutManager(new LinearLayoutManager(context));
//            recyclerView.setAdapter(new TaskAndActionsAdapter(DummyContent.ITEMS));
//        }
        return view;
    }
}