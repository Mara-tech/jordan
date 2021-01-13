package com.mara.jordan.app.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.mara.jordan.app.R;
import com.mara.jordan.app.db.JordanServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.mara.jordan.app.utils.SerDeUtils.serialize;

public class ExportServerDialog extends DialogFragment {

    private static final String TAG = "ExportServerDialog";
    private static final CharSequence CLIPBOARD_EXPORT_LABEL = "Jordan Servers export";
    private JordanServer[] items = new JordanServer[0];
    private ServerImportExportCallback callback;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        String[] itemsAsString = extractName(items);
        boolean[] checked = extractIsChecked(items);
        builder
                .setTitle(R.string.export_server_dialog_title)
                .setMultiChoiceItems(itemsAsString, checked, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                checked[which] = isChecked;
            }
        })
        .setPositiveButton(R.string.server_export_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.i(TAG, "Positive click on " + Arrays.toString(checked));
                Collection<JordanServer> selected = select(items, checked);
                String serialized = serialize(selected);
                toClipBoard(serialized);
                if(callback != null){
                    callback.onServerExported(selected.size());
                }
            }
        });
        return builder.create();
    }

    private void toClipBoard(String serialized) {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(CLIPBOARD_EXPORT_LABEL, serialized);
        clipboard.setPrimaryClip(clip);
    }


    private Collection<JordanServer> select(JordanServer[] items, boolean[] checked) {
        if(items.length != checked.length){
            throw new IllegalArgumentException("items and checked arguments must have same length");
        }
        Collection<JordanServer> selectedItems = new ArrayList<>(items.length);
        for(int i = 0; i < items.length; i++){
            if(checked[i]){
                selectedItems.add(items[i]);
            }
        }
        return selectedItems;
    }

    private boolean[] extractIsChecked(JordanServer[] items) {
        boolean[] boolArr = new boolean[items.length];
        Arrays.fill(boolArr, true);
        return boolArr;
    }

    private String[] extractName(JordanServer[] items) {
//        return Stream.of(items).map(JordanServer::getName).collect(Collectors.toList()).toArray(new String[]{});
        String[] array = new String[items.length];
        for (int i = 0 ; i < items.length; i++) {
            String name = items[i].getName();
            array[i] = name;
        }
        return array;
    }

    public void setItems(List<JordanServer> serversList) {
        items = serversList.toArray(new JordanServer[serversList.size()]);
    }

    public void setCallback(ServerImportExportCallback callback) {
        this.callback = callback;
    }
}