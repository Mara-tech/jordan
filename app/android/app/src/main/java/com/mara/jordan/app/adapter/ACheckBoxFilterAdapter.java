package com.mara.jordan.app.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.google.common.collect.Maps;
import com.mara.jordan.app.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ACheckBoxFilterAdapter<T> extends ArrayAdapter<String> {

    private List<String> items;
    private Map<String, Boolean> itemsChecked;
    private Map<String, Boolean> tempView;
    private LayoutInflater mInflater;


    public ACheckBoxFilterAdapter(Context ctx) {
        super(ctx, 0);
        mInflater = LayoutInflater.from(ctx);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.filter_dialog_item, parent, false);

        CheckBox checkBox = view.findViewById(R.id.filter_item_check);
        String name = items.get(position);
        boolean checked = itemsChecked.get(name);
        checkBox.setText(name);
        checkBox.setChecked(checked);
        tempView.put(name, checked);

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                tempView.put(name, isChecked);
            }
        });

        return view;
    }

    public void applyTempView() {
        itemsChecked = Maps.newHashMap(tempView);
    }

    public void resetTempState() {
        tempView = Maps.newHashMap(itemsChecked);
    }
    
    public Map<String, Boolean> getFilterMapping() {
        return itemsChecked;
    }

    abstract String getTag();

    public void onItemsLoaded(T[] dtos) {
        items = prepareItems(dtos);
        itemsChecked = initCheckedItems();
        resetTempState();
        clear();
        addAll(items);
    }

    /**
     *
     * Example : return Stream.of(dtos).map(JordanMessageStateDTO::getParentTask).map(JordanParentTaskDTO::getName).distinct().sorted().collect(Collectors.toList());
     */
    protected abstract List<String> prepareItems(T[] dtos);

    protected Map<String, Boolean> initCheckedItems() {
//        return items.stream().collect(Collectors.toMap(Function.identity(), this::itemToCheckedInitState));
        Map<String, Boolean> checked = new HashMap<>();
        for (String item : items) {
            if (checked.put(item, itemToCheckedInitState(item)) != null) {
                Log.e(getTag(), "Duplicate key item " + item);
            }
        }
        return checked;
    }

    protected Boolean itemToCheckedInitState(String item) {
        //TODO define retention/persistence policy
        if(itemsChecked != null){
            if(itemsChecked.containsKey(item)){
                return itemsChecked.get(item);
            }
        }
        return true;
    }

}