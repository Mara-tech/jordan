package com.mara.jordan.app.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanLoginCallback;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.app.model.JordanServerModel;
import com.mara.jordan.core.dto.JordanAdminSessionDTO;

import org.apache.commons.lang3.StringUtils;

/**
 * Asks for the operator credentials of the current server and exchanges them for a session
 * token through {@code POST /jordan/admin/login}. Shown when the server refuses a call with a
 * 401, or on demand from the menu.
 */
public class LoginDialog extends DialogFragment implements JordanLoginCallback {

    public static final String TAG = "loginDialog";

    private JordanClientModel model;
    private JordanServerModel serverModel;
    private JordanLoginCallback callback;
    private EditText loginField;
    private EditText passwordField;
    private CheckBox rememberCb;
    private Button positiveButton;
    /** Row of this server in the local database, where credentials may be remembered. */
    private JordanServer storedServer;
    /**
     * What was submitted, kept because the choice is applied when the server answers, and the
     * dialog — with its fields — is gone by then.
     */
    private String submittedLogin;
    private String submittedPassword;
    private boolean remembering;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View loginDialogView = requireActivity().getLayoutInflater().inflate(R.layout.login_dialog, null);
        TextView serverView = loginDialogView.findViewById(R.id.login_dialog_server);
        serverView.setText(getString(R.string.login_dialog_server, model().getServerBaseUrl()));
        loginField = loginDialogView.findViewById(R.id.login_dialog_login);
        passwordField = loginDialogView.findViewById(R.id.login_dialog_password);
        rememberCb = loginDialogView.findViewById(R.id.login_dialog_remember);

        final TextWatcher mandatoryFieldsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                refreshEnablePositiveButton();
            }
        };
        loginField.addTextChangedListener(mandatoryFieldsWatcher);
        passwordField.addTextChangedListener(mandatoryFieldsWatcher);

        prefillWithRememberedCredentials();

        return builder
                .setTitle(R.string.login_dialog_title)
                .setView(loginDialogView)
                .setPositiveButton(R.string.login_dialog_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        submit();
                    }
                })
                .setNeutralButton(R.string.cancel, null)
                .create();
    }

    private void submit() {
        submittedLogin = loginField.getText().toString();
        submittedPassword = passwordField.getText().toString();
        remembering = rememberCb.isChecked();
        model().login(submittedLogin, submittedPassword, this);
    }

    /**
     * The credentials the user asked to remember for this server, when there are any.
     */
    private void prefillWithRememberedCredentials() {
        serverModel = new JordanServerModel(requireContext());
        serverModel.findServer(model().getServerBaseUrl(), this::prefill);
    }

    private void prefill(@Nullable JordanServer server) {
        storedServer = server;
        if (server == null || loginField == null || passwordField == null) {
            return;
        }
        if (StringUtils.isNotEmpty(server.getLogin())) {
            loginField.setText(server.getLogin());
        }
        if (StringUtils.isNotEmpty(server.getPassword())) {
            passwordField.setText(server.getPassword());
        }
        refreshEnablePositiveButton();
    }

    /**
     * A session is open : the credentials that opened it are worth keeping — or worth dropping,
     * including those remembered earlier, when the user no longer wants them on this device.
     */
    private void applyRememberChoice() {
        if (storedServer == null || serverModel == null) {
            return;
        }
        if (remembering) {
            serverModel.rememberCredentials(storedServer, submittedLogin, submittedPassword);
        } else if (storedServer.getLogin() != null || storedServer.getPassword() != null) {
            serverModel.rememberCredentials(storedServer, null, null);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        positiveButton = ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON_POSITIVE);
        refreshEnablePositiveButton();
    }

    private void refreshEnablePositiveButton() {
        if (positiveButton != null) {
            positiveButton.setEnabled(StringUtils.isNotEmpty(loginField.getText())
                    && StringUtils.isNotEmpty(passwordField.getText()));
        }
    }

    @Override
    public void onLoggedIn(JordanAdminSessionDTO session) {
        applyRememberChoice();
        if (callback != null) {
            callback.onLoggedIn(session);
        }
    }

    @Override
    public void onLoginError(String errorMessage) {
        if (callback != null) {
            callback.onLoginError(errorMessage);
        }
    }

    public void setModel(JordanClientModel model) {
        this.model = model;
    }

    /**
     * When the system recreates the dialog without its host (rotation), the model is gone but
     * the session and the current server, which live in the API singleton, are not.
     */
    private JordanClientModel model() {
        if (model == null) {
            model = new JordanClientModel(requireContext());
        }
        return model;
    }

    public void setCallback(JordanLoginCallback callback) {
        this.callback = callback;
    }
}
