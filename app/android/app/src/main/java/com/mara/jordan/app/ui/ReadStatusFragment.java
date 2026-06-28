package com.mara.jordan.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.SeekBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.ReadStatusAdapter;
import com.mara.jordan.app.adapter.StatusFilterTaskAdapter;
import com.mara.jordan.app.adapter.StatusFilterTypeAdapter;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.core.dto.JordanStatusDTO;

import java.util.Map;

public class ReadStatusFragment extends Fragment implements JordanReadStatusCallback {

    public static final String TAG = "STATUS_FRAG";
    /**
     * Displayed elements in the NumberPicker. These values will be the ones sent to Jordan API.
     */
    private static final String[] DEPTH_CHOICES = {"10", "20", "30", "50", "100", "200", "500", "1000"};
    /**
     * NumberPicker requires {@link ReadStatusFragment#DEPTH_CHOICES} is "{@link ReadStatusFragment#MAX_DEPTH_FOR_PICKER} - {@link ReadStatusFragment#MIN_DEPTH_FOR_PICKER} + 1" long
     */
    private static final int MIN_DEPTH_FOR_PICKER = 0;
    /**
     * NumberPicker requires {@link ReadStatusFragment#DEPTH_CHOICES} is "{@link ReadStatusFragment#MAX_DEPTH_FOR_PICKER} - {@link ReadStatusFragment#MIN_DEPTH_FOR_PICKER} + 1" long
     */
    private static final int MAX_DEPTH_FOR_PICKER = DEPTH_CHOICES.length - 1;
    /**
     * default element selected in {@link ReadStatusFragment#DEPTH_CHOICES}
     */
    private static final int DEFAULT_DEPTH = 1;
    /**
     * Default check box state.
     */
    private static final boolean DEFAULT_AUTO_REFRESH = false;
    /**
     * Displayed elements in the NumberPicker. It will set a Scheduler to run periodically.
     */
    private static final String[] PERIOD_CHOICES = {"1", "5", "10", "30", "60", "300"};
    /**
     * NumberPicker requires {@link ReadStatusFragment#PERIOD_CHOICES} is "{@link ReadStatusFragment#MAX_PERIOD_FOR_PICKER} - {@link ReadStatusFragment#MIN_PERIOD_FOR_PICKER} + 1" long
     */
    private static final int MIN_PERIOD_FOR_PICKER = 0;
    /**
     * NumberPicker requires {@link ReadStatusFragment#PERIOD_CHOICES} is "{@link ReadStatusFragment#MAX_PERIOD_FOR_PICKER} - {@link ReadStatusFragment#MIN_PERIOD_FOR_PICKER} + 1" long
     */
    private static final int MAX_PERIOD_FOR_PICKER = PERIOD_CHOICES.length - 1;
    /**
     * default element selected in {@link ReadStatusFragment#PERIOD_CHOICES}
     */
    private static final int DEFAULT_PERIOD = 2;

    static {
        //Ensure choices are valid integers
        for(String p : DEPTH_CHOICES){
            Integer.parseInt(p);
        }
        for(String p : PERIOD_CHOICES){
            Integer.parseInt(p);
        }
    }

    private SwipeRefreshLayout statusListRefreshLayout;
    private ListView statusList;
    private View popupLayout;
    private PopupWindow popup;

    private StatusFilterTypeAdapter statusFilterTypeAdapter;
    private StatusFilterTaskAdapter statusFilterTaskAdapter;
    private String currentSearchQuery;
    private Map<String, Boolean> typeFilter;
    private Map<String, Boolean> taskFilter;
    private JordanTaskModel model;
    private ReadStatusAdapter statusAdapter;
    /**
     * element selected by user in {@link ReadStatusFragment#DEPTH_CHOICES}
     */
    private int statusDepth = DEFAULT_DEPTH;
    private boolean autoRefreshEnabled = DEFAULT_AUTO_REFRESH;
    /**
     * element selected by user in {@link ReadStatusFragment#PERIOD_CHOICES}
     */
    private int autoRefreshPeriod = DEFAULT_PERIOD;

    private final Handler autoRefreshScheduler = new Handler();
    private Runnable autoRefreshRunnable = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ReadStatusFragment() {
    }

    public static ReadStatusFragment newInstance(JordanTaskModel model) {
        ReadStatusFragment fragment = new ReadStatusFragment();
//        Bundle args = new Bundle();
//        args.putString(client_id);
//        fragment.setArguments(args);
        fragment.model = model;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusAdapter = new ReadStatusAdapter(getContext(), model);
        statusFilterTypeAdapter = new StatusFilterTypeAdapter(getContext());
        statusFilterTaskAdapter = new StatusFilterTaskAdapter(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.read_status_view, container, false);
        setHasOptionsMenu(true);
        statusListRefreshLayout = view.findViewById(R.id.swipe_refresh_status);
        statusListRefreshLayout.setOnRefreshListener(this::refreshStatus);

        statusList = view.findViewById(R.id.read_status_list);
        statusList.setAdapter(statusAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
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
        statusAdapter.select(currentSearchQuery, typeFilter, taskFilter);
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
        int itemId = item.getItemId();
        if (itemId == R.id.refresh_status) {
            refreshStatus();
            return true;
        } else if (itemId == R.id.filter_status) {
            filterStatusDialog();
            return true;
        } else if (itemId == R.id.status_text_size) {
            createTextSizePopup();
            return true;
        } else if (itemId == R.id.status_settings) {
            displayParameters();
            return true;
        } else if (itemId == R.id.status_search) {
            //handled with searchView.setOnQueryTextListener
            return true;
        }

        // User didn't trigger a refresh, let the superclass handle this action
        return super.onOptionsItemSelected(item);

    }

    private void displayParameters() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View parametersDialogView = requireActivity().getLayoutInflater().inflate(R.layout.status_parameters_dialog, null);
        NumberPicker depthPicker = parametersDialogView.findViewById(R.id.status_parameters_depth);
        depthPicker.setMinValue(MIN_DEPTH_FOR_PICKER);
        depthPicker.setMaxValue(MAX_DEPTH_FOR_PICKER);
        depthPicker.setDisplayedValues(DEPTH_CHOICES);
        depthPicker.setValue(statusDepth);

        CheckBox autoRefresh = parametersDialogView.findViewById(R.id.status_parameters_auto_refresh_cb);
        autoRefresh.setChecked(autoRefreshEnabled);
        NumberPicker periodPicker = parametersDialogView.findViewById(R.id.status_parameters_auto_refresh_period);
        periodPicker.setEnabled(autoRefresh.isChecked());
        periodPicker.setMinValue(MIN_PERIOD_FOR_PICKER);
        periodPicker.setMaxValue(MAX_PERIOD_FOR_PICKER);
        periodPicker.setDisplayedValues(PERIOD_CHOICES);
        periodPicker.setValue(autoRefreshPeriod);


        autoRefresh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                periodPicker.setEnabled(isChecked);
            }
        });

        builder.setView(parametersDialogView);
        builder.setPositiveButton(R.string.status_parameters_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                statusDepth = depthPicker.getValue();
                autoRefreshEnabled = autoRefresh.isChecked();
                autoRefreshPeriod = periodPicker.getValue();
                refreshStatus();
            }
        });

        builder.show();


    }

    /**
     * Decides whether next (auto-)refresh should be scheduled, or not.
     */
    private void setupAutoRefresh() {
        if(getLifecycle().getCurrentState() == Lifecycle.State.RESUMED) {
            if (autoRefreshRunnable == null) {
                if (autoRefreshEnabled) {
                    scheduleNextRefresh();
                } else {
                    //Do nothing, auto refresh stays disabled
                }
            } else {
                if (autoRefreshEnabled) {
                    cancelScheduledRefresh(); //Period may have changed, so cancel the previous period
                    scheduleNextRefresh(); //and set the new one
                } else {
                    cancelScheduledRefresh();
                }
            }
        } else {
            //Fragment not visible, do not schedule anything
        }
    }

    private void cancelScheduledRefresh() {
        if (autoRefreshRunnable != null) {
            autoRefreshScheduler.removeCallbacks(autoRefreshRunnable);
            autoRefreshRunnable = null;
//            stopped auto refresh
        }
    }

    private void scheduleNextRefresh() {
        autoRefreshRunnable = this::refreshStatus;
        autoRefreshScheduler.postDelayed(autoRefreshRunnable, 1000 * Integer.parseInt(PERIOD_CHOICES[autoRefreshPeriod]));
        Log.d(TAG, "Refresh scheduled in " + Integer.parseInt(PERIOD_CHOICES[autoRefreshPeriod]) + "sec");
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
        cancelScheduledRefresh();
    }

    /**
     * Show dialog with task+type checkboxes
      */
    private void filterStatusDialog() {

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
                        setStatusFilters(statusFilterTypeAdapter.getFilterMapping(),
                                statusFilterTaskAdapter.getFilterMapping());
                    }
                })
                .setNeutralButton(R.string.cancel, (dialog, which) -> {})

                .show();

    }

    private void refreshStatus() {
        if(!statusListRefreshLayout.isRefreshing()){
            statusListRefreshLayout.setRefreshing(true);
        }
        statusAdapter.refresh(currentSearchQuery, typeFilter, taskFilter, Integer.parseInt(DEPTH_CHOICES[statusDepth]), this);
    }

    @Override
    public void onStatusLoaded(JordanStatusDTO[] statuses) {
        statusListRefreshLayout.setRefreshing(false);
        statusFilterTypeAdapter.onItemsLoaded(statuses);
        statusFilterTaskAdapter.onItemsLoaded(statuses);
        if(statuses.length == 0){
            if(getView() != null){
                Snackbar.make(getView(), R.string.no_status_to_display, Snackbar.LENGTH_SHORT).show();
            }
        }
        Log.i(TAG, "status loaded success");
        setupAutoRefresh();
    }

    @Override
    public void onStatusLoadingError(String errorMessage) {
        statusListRefreshLayout.setRefreshing(false);
        if(getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.status_refresh_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.status_refresh_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.status_refresh_failure_details_dialog)
                                    .setItems(new String[]{errorMessage}, null)
                                    .show();
                        }
                    })
                    .show();
        }
        Log.i(TAG, "status loaded failure");
        setupAutoRefresh();
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
