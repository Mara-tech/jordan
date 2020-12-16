package com.mara.jordan.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.ReadStatusAdapter;
import com.mara.jordan.app.adapter.StatusFilterTaskAdapter;
import com.mara.jordan.app.adapter.StatusFilterTypeAdapter;
import com.mara.jordan.app.model.dummy.MockDatabase;

import java.util.List;
import java.util.Map;

public class ReadStatusFragment extends Fragment {

    public static final String TAG = "STATUS_FRAG";

    private SwipeRefreshLayout statusListRefreshLayout;
    private ListView statusList;
    private View popupLayout;
    private PopupWindow popup;

    private StatusFilterTypeAdapter statusFilterTypeAdapter;
    private StatusFilterTaskAdapter statusFilterTaskAdapter;
    private String currentSearchQuery;
    private Map<String, Boolean> typeFilter;
    private Map<String, Boolean> taskFilter;

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

        statusList = view.findViewById(R.id.read_status_list);
        updateStatusAdapter();

        statusFilterTypeAdapter = new StatusFilterTypeAdapter(getContext());
        statusFilterTaskAdapter = new StatusFilterTaskAdapter(getContext());


        return view;
    }

    private void setCurrentSearchQuery(String query) {
        currentSearchQuery = query;
        updateStatusAdapter();
    }

    private void clearCurrentSearchQuery() {
        currentSearchQuery = null;
        updateStatusAdapter();
    }

    private void setStatusFilters(Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter) {
        this.typeFilter = typeFilter;
        this.taskFilter = taskFilter;
        updateStatusAdapter();
    }

    private void updateStatusAdapter() {
        List<MockDatabase.EasyStatus> items = MockDatabase.selectStatus(currentSearchQuery, typeFilter, taskFilter);
        ListAdapter statusAdapter = new ReadStatusAdapter(getContext(), items);
        statusList.setAdapter(statusAdapter);
    }



    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.read_status_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.status_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                setCurrentSearchQuery(newText);
                return false;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                clearCurrentSearchQuery();
                return false;
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.refresh_status:
                statusListRefreshLayout.setRefreshing(true);
                refreshStatus();
                return true;
            case R.id.filter_status:
                filterStatusSelected();
                return true;
            case R.id.status_text_size:
                createTextSizePopup();
                return true;
            case R.id.status_search:
                //handled with searchView.setOnQueryTextListener
                return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void createTextSizePopup() {
        if(popupLayout == null){
            popupLayout = getLayoutInflater().inflate(R.layout.text_size_bar_layout, null);
            popupLayout.setOnClickListener(v -> {if(popup != null)popup.dismiss();});
            final SeekBar textSizeBar = popupLayout.findViewById(R.id.text_size_seek_bar);
            textSizeBar.setMax(StatusTextSizeHelper.getSeekbarMax());
            textSizeBar.setProgress(StatusTextSizeHelper.getSeekbarInitialProgress());

            textSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if(statusList != null && statusList.getAdapter() != null){
                        ((ReadStatusAdapter)statusList.getAdapter()).changeTextSize(StatusTextSizeHelper.convertProgressToTextSize(progress));
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        popup = new PopupWindow(popupLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setOutsideTouchable(true);
        popup.showAtLocation(popupLayout, Gravity.END|Gravity.TOP, 0, 0);
        popup.setFocusable(true);
        popup.setOnDismissListener(() -> popup = null);
    }

    @Override
    public void onStop() {
        super.onStop();
        if(popup != null){
            popup.dismiss();
        }
    }

    /**
     * Show dialog with task+type checkboxes
      */
    private void filterStatusSelected() {

        statusFilterTypeAdapter.resetTempState();
        statusFilterTaskAdapter.resetTempState();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View filterDialogView = requireActivity().getLayoutInflater().inflate(R.layout.status_filter_dialog, null);
        ListView typeList = filterDialogView.findViewById(R.id.status_filter_type_list);
        typeList.setAdapter(statusFilterTypeAdapter);
        ListView taskList = filterDialogView.findViewById(R.id.status_filter_task_list);
        taskList.setAdapter(statusFilterTaskAdapter);

        builder.setView(filterDialogView)
                .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        statusFilterTypeAdapter.applyTempView();
                        statusFilterTaskAdapter.applyTempView();
                        setStatusFilters(statusFilterTypeAdapter.getFilterMapping(), statusFilterTaskAdapter.getFilterMapping());
                    }
                })
                .setNeutralButton(R.string.cancel, (dialog, which) -> {})

                .create().show();

    }

    private void refreshStatus() {
        //start async refresh clients
        Snackbar.make(getView(), "Refreshing status...", Snackbar.LENGTH_SHORT).show();
        statusListRefreshLayout.setRefreshing(false);
    }


    public static class StatusTextSizeHelper {
        /**
         * size in sp ; should not be smaller than that.
         */
        public static final int MIN_TEXT_SIZE = 8;
        /**
         * size in sp ; should not be larger than that.
         */
        public static final int MAX_TEXT_SIZE = 40;
        /**
         * size in sp ; initial text size.
         */
        public static final int DEFAULT_TEXT_SIZE = 14;

        /**
         * Because of {@link SeekBar#setMin(int)} exists from API 26 only, shift is done here.
         * @return {@link StatusTextSizeHelper#DEFAULT_TEXT_SIZE} minus {@link StatusTextSizeHelper#MIN_TEXT_SIZE}
         */
        public static int getSeekbarInitialProgress() {
            return DEFAULT_TEXT_SIZE - MIN_TEXT_SIZE;
        }

        /**
         * Because of {@link SeekBar#setMin(int)} exists from API 26 only, shift is done here.
         * @return {@link StatusTextSizeHelper#MAX_TEXT_SIZE} minus {@link StatusTextSizeHelper#MIN_TEXT_SIZE}
         */
        public static int getSeekbarMax() {
            return MAX_TEXT_SIZE - MIN_TEXT_SIZE;
        }

        /**
         * Because of {@link SeekBar#setMin(int)} exists from API 26 only, shift is done here.
         * @return {@code seekBarProgress} plus {@link StatusTextSizeHelper#MIN_TEXT_SIZE}
         */
        public static int convertProgressToTextSize(int seekBarProgress) {
            return seekBarProgress + MIN_TEXT_SIZE;
        }
    }

}
