package com.mara.jordan.app.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.gson.JsonSyntaxException;
import com.mara.jordan.app.R;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.utils.SerDeUtils;

public class ImportServerDialog extends DialogFragment {

    private static final String TAG = "ImportServerDialog";
    private static final String CLIPBOARD_IMPORT_METHOD = "CLIPBOARD";
    private static final String[] IMPORT_METHODS = {CLIPBOARD_IMPORT_METHOD};
    private ServerImportExportCallback callback;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder
                .setTitle(R.string.import_server_dialog_title)
                .setItems(IMPORT_METHODS, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        importMethod(IMPORT_METHODS[which]);
                    }
                })
                ;

        return builder.create();
    }

    private void importMethod(String importMethod) {
        switch (importMethod){
            case CLIPBOARD_IMPORT_METHOD:
                importFromClipboard();
                break;
            default:
                throw new IllegalArgumentException("Method " + importMethod + " is not defined. Check " + TAG);
        }
    }

    private void importFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)){
            ClipData.Item clip = clipboard.getPrimaryClip().getItemAt(0);
            String data = String.valueOf(clip.getText());
            try{
                JordanServer[] servers = SerDeUtils.deserialize(data, JordanServer[].class);
                if(callback != null){
                    callback.onServerImportParsed(servers);
                }
            } catch (JsonSyntaxException parseError){
                Log.e(TAG, "Error while importing from clipboard (see clipboard content below)", parseError);
                Log.e(TAG, data);
                if(getContext() != null){
                    callback.onServerImportError(getContext().getString(R.string.server_import_clipboard_parse_error), parseError.getMessage());
                }
            }
        } else {
            if(getContext() != null){
                callback.onServerImportError(getContext().getString(R.string.server_import_clipboard_invalid));
            }
        }


    }


    public void setCallback(ServerImportExportCallback callback) {
        this.callback = callback;
    }
}
