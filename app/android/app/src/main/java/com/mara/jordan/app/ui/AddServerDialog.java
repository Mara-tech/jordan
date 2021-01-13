package com.mara.jordan.app.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.mara.jordan.app.R;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.model.JordanServerModel;
import com.mara.jordan.app.model.dto.JordanTestDTO;
import com.mara.jordan.app.utils.CircularProgressButtonHelper;
import com.mara.jordan.app.utils.NetworkUtils;

import org.apache.commons.lang3.StringUtils;

import br.com.simplepass.loadingbutton.customViews.CircularProgressButton;

public class AddServerDialog extends DialogFragment implements ServerConnectionTestCallback {

    private JordanServerModel model;
    private ServerAddOrUpdateCallback callback;
    private JordanServer updatingEntity;
    private EditText serverNameField;
    private EditText serverBaseUriField;
    private CheckBox rememberLoginCb;
    private EditText loginField;
    private CheckBox rememberPasswordCb;
    private EditText passwordField;
    private Button positiveButton;
    private CircularProgressButton testConnectionButton;
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
        View addServerDialogView = requireActivity().getLayoutInflater().inflate(R.layout.add_server_dialog, null);
        serverNameField = addServerDialogView.findViewById(R.id.add_server_dialog_server_name);
        serverBaseUriField = addServerDialogView.findViewById(R.id.add_server_dialog_server_base_uri);
        testConnectionButton = addServerDialogView.findViewById(R.id.add_server_dialog_test_connection);
        rememberLoginCb = addServerDialogView.findViewById(R.id.add_server_dialog_remember_login);
        loginField = addServerDialogView.findViewById(R.id.add_server_dialog_login);
        rememberPasswordCb = addServerDialogView.findViewById(R.id.add_server_dialog_remember_password);
        passwordField = addServerDialogView.findViewById(R.id.add_server_dialog_password);
        builder
                .setTitle(R.string.add_server_dialog_title)
                .setView(addServerDialogView)
                .setPositiveButton(
                        isUpdate() ?
                                R.string.update_server_dialog_confirm
                                : R.string.add_server_dialog_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        JordanServer entity = makeEntity(addServerDialogView, updatingEntity);
                        if (isUpdate()) {
                            callback.updateServer(entity);
                        } else {
                            callback.addServer(entity);
                        }
                    }
                })
                .setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Do nothing
                    }
                });


        final TextWatcher mandatoryFieldsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                refreshEnablePositiveButton(serverNameField, serverBaseUriField);
            }
        };
        serverNameField.addTextChangedListener(mandatoryFieldsWatcher);
        serverBaseUriField.addTextChangedListener(mandatoryFieldsWatcher);
        serverBaseUriField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if(testConnectionButton != null){
                    testConnectionButton.revertAnimation();
                }
            }
        });
        testConnectionButton.setOnClickListener(v->initServerConnectionTest());
        rememberLoginCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                loginField.setEnabled(isChecked);
                rememberPasswordCb.setEnabled(isChecked);
                passwordField.setEnabled(isChecked);
            }
        });
        rememberPasswordCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                passwordField.setEnabled(isChecked);
            }
        });


        if(isUpdate()){
            serverNameField.setText(updatingEntity.getName());
            serverBaseUriField.setText(updatingEntity.getUrl());
            rememberLoginCb.setChecked(updatingEntity.getLogin() != null);
            if(updatingEntity.getLogin() != null){
                loginField.setText(updatingEntity.getLogin());
            }
            rememberPasswordCb.setChecked(updatingEntity.getPassword() != null);
            if(updatingEntity.getPassword() != null){
                passwordField.setText(updatingEntity.getPassword());
            }
        }

        AlertDialog dialog = builder.create();
        return dialog;
    }

    private void initServerConnectionTest() {
        if(model != null && serverBaseUriField != null){
            testConnectionButton.startAnimation();
            model.testConnection(serverBaseUriField.getText().toString(), this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        positiveButton = ((AlertDialog)getDialog()).getButton(DialogInterface.BUTTON_POSITIVE);
        refreshEnablePositiveButton(serverNameField, serverBaseUriField);
    }


    private void refreshEnablePositiveButton(EditText... mandatoryFields) {
        if(positiveButton != null){
            positiveButton.setEnabled(areMandatoryFieldsValid(mandatoryFields));
        }
    }

    private boolean areMandatoryFieldsValid(EditText[] mandatoryFields) {
        // return Stream.of(mandatoryFields).map(et -> StringUtils.isNotEmpty(et.getText())).reduce(Boolean.TRUE, Boolean::logicalAnd)
        boolean acc = true;
        for (EditText et : mandatoryFields) {
            boolean notEmpty = StringUtils.isNotEmpty(et.getText());
            acc &= notEmpty;
        }
        return acc;
    }

    private boolean isUpdate() {
        return updatingEntity != null;
    }

    private JordanServer makeEntity(View dialogView, JordanServer updatingEntity) {
        String serverName = serverNameField.getText().toString();
        String serverBaseUri = NetworkUtils.removeEndingSlash(serverBaseUriField.getText().toString());
        String login = loginField.getText().toString();
        String password = passwordField.getText().toString();
        boolean rememberLogin = rememberLoginCb.isChecked();
        boolean rememberPassword = rememberLogin && rememberPasswordCb.isChecked();

        final JordanServer.JordanServerBuilder entityBuilder = JordanServer.builder()
                .name(serverName)
                .url(serverBaseUri);
        if(rememberLogin){
            entityBuilder.login(login);
            if(rememberPassword){
                entityBuilder.password(password);
            }
        }

        if(isUpdate()){
            entityBuilder.id(this.updatingEntity.getId());
        }

        return entityBuilder.build();
    }

    public void setAddServerDialogListener(ServerAddOrUpdateCallback callback) {
        this.callback = callback;
    }

    public void setServerToBeUpdated(JordanServer prefillServer) {
        updatingEntity = prefillServer;
    }

    public void setModel(JordanServerModel serverModel) {
        model = serverModel;
    }

    @Override
    public void onConnectionTestPassed(JordanTestDTO response) {
        if(testConnectionButton != null){
            testConnectionButton.doneLoadingAnimation(cpbh.getProgressionButtonFillColor(), cpbh.getSuccessBitmap());
        }
    }

    @Override
    public void onConnectionTestError(Throwable error) {
        if(testConnectionButton != null){
            testConnectionButton.doneLoadingAnimation(cpbh.getProgressionButtonFillColor(), cpbh.getErrorBitmap());
        }
        if(error != null && error.getMessage() != null){
            Toast.makeText(getContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
