package com.mara.jordan.app.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.mara.jordan.app.R;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.utils.CircularProgressButtonHelper;

import org.apache.commons.lang3.StringUtils;

import br.com.simplepass.loadingbutton.customViews.CircularProgressButton;

public class GenericInformationDialog extends DialogFragment implements GenericQueryCallback {
    private JordanClientModel model;
    private TextView resultField;
    private CircularProgressButton queryButton;
    private CircularProgressButtonHelper cpbh;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cpbh = CircularProgressButtonHelper.getInstance(getContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View genericInformationDialogView = requireActivity().getLayoutInflater().inflate(R.layout.generic_information_dialog, null);
        EditText queryField = genericInformationDialogView.findViewById(R.id.generic_information_query_field);
        queryButton = genericInformationDialogView.findViewById(R.id.generic_information_query_button);
        resultField = genericInformationDialogView.findViewById(R.id.generic_information_result_field);
        builder
                .setTitle(R.string.generic_information_dialog_title)
                .setView(genericInformationDialogView)
                .setNeutralButton(R.string.generic_information_quit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Do nothing
                    }
                });


        final TextWatcher mandatoryFieldWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                queryButton.setEnabled(
                        s != null && StringUtils.isNotEmpty(s.toString())
                );
            }
        };
        queryField.addTextChangedListener(mandatoryFieldWatcher);
        queryField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if(actionId == EditorInfo.IME_ACTION_SEARCH){
                    queryButton.performClick();
                    handled = true;
                }
                return handled;
            }
        });
        queryButton.setOnClickListener(v-> genericQuery(queryField.getText()));

        AlertDialog dialog = builder.create();
        return dialog;
    }

    private void genericQuery(Editable query) {
        if(query != null && StringUtils.isNotEmpty(query.toString())){
            queryButton.startAnimation();
            model.genericQuery(query.toString(), this);
        }
    }

    public void setModel(JordanClientModel model) {
        this.model = model;
    }

    @Override
    public void onGenericQueryError(String errorMessage) {
        cpbh.errorAndReset(queryButton);
        if(getContext() != null){
            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onGenericQueryResponse(String response) {
        cpbh.successAndReset(queryButton);
        if(StringUtils.isNotEmpty(response)){
            resultField.setText(response);
        } else {
            resultField.setText(R.string.generic_information_query_no_result);
        }
    }
}
