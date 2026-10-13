package com.mara.jordan.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.re2j.PatternSyntaxException;
import com.mara.jordan.app.R;
import com.mara.jordan.app.adapter.ReadStatusAdapter;
import com.mara.jordan.app.adapter.StatusFilterTaskAdapter;
import com.mara.jordan.app.adapter.StatusFilterTypeAdapter;
import com.mara.jordan.app.api.JordanReadStatusCallback;
import com.mara.jordan.app.model.JordanTaskModel;
import com.mara.jordan.app.model.StatusTextFilter;
import com.mara.jordan.core.dto.JordanStatusDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ReadStatusFragment extends Fragment implements JordanReadStatusCallback, JordanRefreshable {

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
    /**
     * Keys of the filters (JRD-31) and settings (JRD-37) in the saved state, which a rotation would
     * otherwise lose.
     */
    private static final String STATE_SEARCH = "status.search";
    private static final String STATE_TEXT = "status.text";
    private static final String STATE_TEXT_REGEX = "status.text.regex";
    private static final String STATE_TYPE_FILTER = "status.type";
    private static final String STATE_TASK_FILTER = "status.task";
    private static final String STATE_DEPTH = "status.depth";
    private static final String STATE_AUTO_REFRESH = "status.auto_refresh";
    private static final String STATE_AUTO_REFRESH_PERIOD = "status.auto_refresh.period";
    private static final String STATE_TEXT_SIZE = "status.text_size";

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
    private TextView emptyView;
    private View popupLayout;
    private PopupWindow popup;

    private StatusFilterTypeAdapter statusFilterTypeAdapter;
    private StatusFilterTaskAdapter statusFilterTaskAdapter;
    private String currentSearchQuery;
    /**
     * Text of the filter dialog, applied on top of {@link ReadStatusFragment#currentSearchQuery}.
     */
    private StatusTextFilter textFilter = StatusTextFilter.NONE;
    private Map<String, Boolean> typeFilter;
    private Map<String, Boolean> taskFilter;
    private JordanTaskModel model;
    private ReadStatusAdapter statusAdapter;
    /**
     * How many statuses the last reading returned, before any filter ; -1 until one arrives.
     */
    private int readStatusCount = -1;
    /**
     * element selected by user in {@link ReadStatusFragment#DEPTH_CHOICES}
     */
    private int statusDepth = DEFAULT_DEPTH;
    private boolean autoRefreshEnabled = DEFAULT_AUTO_REFRESH;
    /**
     * element selected by user in {@link ReadStatusFragment#PERIOD_CHOICES}
     */
    private int autoRefreshPeriod = DEFAULT_PERIOD;
    /**
     * size in sp of the statuses, chosen with the bar of {@link #createTextSizePopup()}
     */
    private int statusTextSize = StatusTextSizeHelper.DEFAULT_TEXT_SIZE;

    private final Handler autoRefreshScheduler = new Handler();
    private Runnable autoRefreshRunnable = null;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ReadStatusFragment() {
    }

    public static ReadStatusFragment newInstance() {
        return new ReadStatusFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = ClientInteractionsFragment.taskModelOf(this);
        statusAdapter = new ReadStatusAdapter(getContext(), model);
        statusFilterTypeAdapter = new StatusFilterTypeAdapter(getContext());
        statusFilterTaskAdapter = new StatusFilterTaskAdapter(getContext());
        if (savedInstanceState != null) {
            restoreFilters(savedInstanceState);
            restoreSettings(savedInstanceState);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SEARCH, currentSearchQuery);
        outState.putString(STATE_TEXT, textFilter.getText());
        outState.putBoolean(STATE_TEXT_REGEX, textFilter.isRegex());
        putFilterMapping(outState, STATE_TYPE_FILTER, typeFilter);
        putFilterMapping(outState, STATE_TASK_FILTER, taskFilter);
        outState.putInt(STATE_DEPTH, statusDepth);
        outState.putBoolean(STATE_AUTO_REFRESH, autoRefreshEnabled);
        outState.putInt(STATE_AUTO_REFRESH_PERIOD, autoRefreshPeriod);
        outState.putInt(STATE_TEXT_SIZE, statusTextSize);
    }

    /**
     * Puts back the settings {@link #onSaveInstanceState} saved (JRD-37) : the depth of the
     * reading, the auto-refresh — which {@link #onStatusLoaded} schedules again after the first
     * reading — and the text size.
     */
    private void restoreSettings(Bundle savedState) {
        statusDepth = savedState.getInt(STATE_DEPTH, DEFAULT_DEPTH);
        autoRefreshEnabled = savedState.getBoolean(STATE_AUTO_REFRESH, DEFAULT_AUTO_REFRESH);
        autoRefreshPeriod = savedState.getInt(STATE_AUTO_REFRESH_PERIOD, DEFAULT_PERIOD);
        setStatusTextSize(savedState.getInt(STATE_TEXT_SIZE, StatusTextSizeHelper.DEFAULT_TEXT_SIZE));
    }

    /**
     * Puts back the filters {@link #onSaveInstanceState} saved, before the first reading applies
     * them : the toolbar search (shown again by {@link #onCreateOptionsMenu}), the text of the
     * dialog, and its type and task checkboxes.
     */
    private void restoreFilters(Bundle savedState) {
        currentSearchQuery = savedState.getString(STATE_SEARCH);
        try {
            textFilter = StatusTextFilter.of(savedState.getString(STATE_TEXT),
                    savedState.getBoolean(STATE_TEXT_REGEX));
        } catch (PatternSyntaxException e) {
            // cannot happen : only a text that compiled was applied, hence saved
            Log.e(TAG, "Saved text filter no longer compiles, dropped", e);
            textFilter = StatusTextFilter.NONE;
        }
        Map<String, Boolean> savedTypes = getFilterMapping(savedState, STATE_TYPE_FILTER);
        if (savedTypes != null) {
            statusFilterTypeAdapter.restoreFilterMapping(savedTypes);
            typeFilter = statusFilterTypeAdapter.getFilterMapping();
        }
        Map<String, Boolean> savedTasks = getFilterMapping(savedState, STATE_TASK_FILTER);
        if (savedTasks != null) {
            statusFilterTaskAdapter.restoreFilterMapping(savedTasks);
            taskFilter = statusFilterTaskAdapter.getFilterMapping();
        }
    }

    /**
     * @param mapping checkbox name → checked ; nothing is saved when it is {@code null}, no
     *                checkbox having been applied yet
     */
    private static void putFilterMapping(Bundle outState, String key, Map<String, Boolean> mapping) {
        if (mapping == null) {
            return;
        }
        ArrayList<String> names = new ArrayList<>(mapping.keySet());
        boolean[] checked = new boolean[names.size()];
        for (int i = 0; i < checked.length; i++) {
            checked[i] = Boolean.TRUE.equals(mapping.get(names.get(i)));
        }
        outState.putStringArrayList(key + ".names", names);
        outState.putBooleanArray(key + ".checked", checked);
    }

    /**
     * @return what {@link #putFilterMapping} saved under {@code key}, or {@code null} if nothing
     */
    private static Map<String, Boolean> getFilterMapping(Bundle savedState, String key) {
        ArrayList<String> names = savedState.getStringArrayList(key + ".names");
        boolean[] checked = savedState.getBooleanArray(key + ".checked");
        if (names == null || checked == null || names.size() != checked.length) {
            return null;
        }
        Map<String, Boolean> mapping = new HashMap<>();
        for (int i = 0; i < checked.length; i++) {
            mapping.put(names.get(i), checked[i]);
        }
        return mapping;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.read_status_view, container, false);
        setHasOptionsMenu(true);
        statusListRefreshLayout = view.findViewById(R.id.swipe_refresh_status);
        statusListRefreshLayout.setOnRefreshListener(this::refreshStatus);
        emptyView = view.findViewById(R.id.empty_view);

        statusList = view.findViewById(R.id.read_status_list);
        statusList.setAdapter(statusAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    /**
     * What the search of the toolbar calls on each keystroke.
     */
    @VisibleForTesting
    void setCurrentSearchQuery(String query) {
        currentSearchQuery = query;
        updateStatusAdapter();
    }

    /**
     * What the bar of {@link #createTextSizePopup()} calls as it moves.
     *
     * @param textSizeSp size in sp of the statuses
     */
    @VisibleForTesting
    void setStatusTextSize(int textSizeSp) {
        statusTextSize = textSizeSp;
        statusAdapter.changeTextSize(textSizeSp);
    }

    private void clearCurrentSearchQuery() {
        currentSearchQuery = null;
        updateStatusAdapter();
    }

    private void setStatusFilters(StatusTextFilter textFilter, Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter) {
        this.textFilter = textFilter;
        this.typeFilter = typeFilter;
        this.taskFilter = taskFilter;
        updateStatusAdapter();
    }

    private void updateStatusAdapter() {
        statusAdapter.select(currentSearchQuery, textFilter, typeFilter, taskFilter);
        updateEmptyView();
    }

    /**
     * Says why the list is empty, when it is : nothing was read, or the filters hide all that was
     * (JRD-32). Called after every selection of the adapter, since any filter can empty it.
     */
    private void updateEmptyView() {
        if (emptyView == null || readStatusCount < 0) {
            return;
        }
        if (readStatusCount == 0) {
            emptyView.setText(R.string.no_status_to_display);
            emptyView.setVisibility(View.VISIBLE);
        } else if (statusAdapter.getCount() == 0) {
            emptyView.setText(getResources().getQuantityString(R.plurals.statuses_hidden_by_filters,
                    readStatusCount, readStatusCount));
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.read_status_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.status_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        // A menu built anew — after a rotation, or whenever it is invalidated — starts collapsed and
        // empty : show the search that filters the list, never an empty field over a filtered list
        // (JRD-31). Before the listeners, since expanding empties the field.
        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            String search = currentSearchQuery;
            searchItem.expandActionView();
            searchView.setQuery(search, false);
            // expanding asks for the keyboard : the list is what a rotated screen is for
            searchView.clearFocus();
        }
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
            textSizeBar.setProgress(StatusTextSizeHelper.convertTextSizeToProgress(statusTextSize));

            textSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    setStatusTextSize(StatusTextSizeHelper.convertProgressToTextSize(progress));
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
     * Show dialog with text field + task+type checkboxes. The text is checked before anything is
     * applied : an invalid regular expression keeps the dialog open, says why, and leaves every
     * filter as it was.
      */
    private void filterStatusDialog() {

        statusFilterTypeAdapter.resetTempState();
        statusFilterTaskAdapter.resetTempState();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View filterDialogView = requireActivity().getLayoutInflater().inflate(R.layout.status_filter_dialog, null);
        EditText textField = filterDialogView.findViewById(R.id.status_filter_text);
        textField.setText(textFilter.getText());
        CheckBox regexBox = filterDialogView.findViewById(R.id.status_filter_text_regex);
        regexBox.setChecked(textFilter.isRegex());
        TextView textError = filterDialogView.findViewById(R.id.status_filter_text_error);
        textField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                textError.setVisibility(View.GONE);
            }
        });
        regexBox.setOnCheckedChangeListener((buttonView, isChecked) -> textError.setVisibility(View.GONE));
        ListView typeList = filterDialogView.findViewById(R.id.status_filter_type_list);
        typeList.setAdapter(statusFilterTypeAdapter);
        ListView taskList = filterDialogView.findViewById(R.id.status_filter_task_list);
        taskList.setAdapter(statusFilterTaskAdapter);

        AlertDialog dialog = builder.setView(filterDialogView)
                // replaced once shown : the default click dismisses the dialog, an invalid text included
                .setPositiveButton(R.string.apply, null)
                .setNeutralButton(R.string.cancel, (d, which) -> {})

                .show();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            StatusTextFilter appliedText;
            try {
                appliedText = StatusTextFilter.of(textField.getText().toString(), regexBox.isChecked());
            } catch (PatternSyntaxException e) {
                textError.setText(getString(R.string.status_filter_text_invalid, e.getDescription()));
                textError.setVisibility(View.VISIBLE);
                return;
            }
            statusFilterTypeAdapter.applyTempView();
            statusFilterTaskAdapter.applyTempView();
            setStatusFilters(appliedText, statusFilterTypeAdapter.getFilterMapping(),
                    statusFilterTaskAdapter.getFilterMapping());
            dialog.dismiss();
        });

    }

    private void refreshStatus() {
        if(!statusListRefreshLayout.isRefreshing()){
            statusListRefreshLayout.setRefreshing(true);
        }
        statusAdapter.refresh(currentSearchQuery, textFilter, typeFilter, taskFilter, Integer.parseInt(DEPTH_CHOICES[statusDepth]), this);
    }

    @Override
    public void refreshContent() {
        refreshStatus();
    }

    @Override
    public void onStatusLoaded(JordanStatusDTO[] statuses) {
        statusListRefreshLayout.setRefreshing(false);
        statusFilterTypeAdapter.onItemsLoaded(statuses);
        statusFilterTaskAdapter.onItemsLoaded(statuses);
        // the adapter has already selected from these statuses : see ReadStatusAdapter#refresh
        readStatusCount = statuses.length;
        updateEmptyView();
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

        /**
         * Because of {@link SeekBar#setMin(int)} exists from API 26 only, shift is done here.
         * @return {@code textSizeSp} minus {@link StatusTextSizeHelper#MIN_TEXT_SIZE}
         */
        public static int convertTextSizeToProgress(int textSizeSp) {
            return textSizeSp - MIN_TEXT_SIZE;
        }
    }

}
