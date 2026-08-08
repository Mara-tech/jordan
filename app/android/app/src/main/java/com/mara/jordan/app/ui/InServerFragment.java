package com.mara.jordan.app.ui;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.api.JordanAuthenticationListener;
import com.mara.jordan.app.api.JordanLoginCallback;
import com.mara.jordan.app.api.JordanLogoutCallback;
import com.mara.jordan.app.model.JordanClientModel;
import com.mara.jordan.core.dto.JordanAdminSessionDTO;

public abstract class InServerFragment extends Fragment implements FullDeletionCallback,
        JordanAuthenticationListener,
        JordanLoginCallback,
        JordanLogoutCallback,
        JordanRefreshable {

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.server_global_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean authenticated = getModel().isAuthenticated();
        final MenuItem login = menu.findItem(R.id.login);
        final MenuItem logout = menu.findItem(R.id.logout);
        if (login != null && logout != null) {
            login.setVisible(!authenticated);
            logout.setVisible(authenticated);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.generic_information) {
            displayGenericInformationDialog();
            return true;
        } else if (itemId == R.id.delete_all) {
            displayDeleteAllDialog();
            return true;
        } else if (itemId == R.id.login) {
            displayLoginDialog();
            return true;
        } else if (itemId == R.id.logout) {
            getModel().logout(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        // this screen is the visible one : it answers the 401 of the calls it triggers
        getModel().setAuthenticationListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getModel().clearAuthenticationListener(this);
    }

    @Override
    public void onAuthenticationRequired(String serverBaseUrl) {
        displayLoginDialog();
    }

    /**
     * Shown at most once : several calls of the same screen may be refused together.
     * {@code showNow} runs the transaction at once, so that the dialog is findable by tag before
     * the next refused call reaches us — {@code show} only commits at the next loop turn.
     */
    protected void displayLoginDialog() {
        if (getChildFragmentManager().findFragmentByTag(LoginDialog.TAG) != null) {
            return;
        }
        final LoginDialog dialog = new LoginDialog();
        dialog.setModel(getModel());
        dialog.setCallback(this);
        dialog.showNow(getChildFragmentManager(), LoginDialog.TAG);
    }

    @Override
    public void onLoggedIn(JordanAdminSessionDTO session) {
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
        if (getView() != null && getContext() != null) {
            Snackbar.make(getView(),
                    getContext().getString(R.string.login_success, session.getLogin(), session.getRole()),
                    Snackbar.LENGTH_SHORT).show();
        }
        // the calls refused before the session was opened are worth another try
        refreshContent();
    }

    @Override
    public void onLoginError(String errorMessage) {
        if (getView() != null && getContext() != null) {
            Snackbar.make(getView(), errorMessage, Snackbar.LENGTH_LONG)
                    .setAction(R.string.login_retry, v -> displayLoginDialog())
                    .show();
        }
    }

    @Override
    public void onLoggedOut() {
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
        if (getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.logout_success, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLogoutError(String errorMessage) {
        if (getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.logout_failure, Snackbar.LENGTH_LONG).show();
        }
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
    }

    protected void displayDeleteAllDialog() {
        //Check role !
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getContext().getString(R.string.delete_all_confirmation_dialog))
                .setMessage(R.string.delete_all_confirmation_dialog_message)
                .setPositiveButton(R.string.delete_all_confirmation_positive, (d, w) -> confirmDeleteAll())
                .setNegativeButton(R.string.delete_client_confirmation_negative, null)
                .show();    }

    protected void confirmDeleteAll() {
        getModel().deleteAll(this);
    }

    protected void displayGenericInformationDialog() {
        //Check role ?
        final GenericInformationDialog dialog = new GenericInformationDialog();
        dialog.setModel(getModel());
        dialog.show(getChildFragmentManager(), "genericInformation");

    }

    protected abstract JordanClientModel getModel();

    @Override
    public void onBaseDeleted() {
        if(getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.delete_all_success, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBaseDeletionError(String errorMessage) {
        if(getView() != null && getContext() != null) {
            Snackbar.make(getView(), R.string.delete_all_failure, Snackbar.LENGTH_LONG)
                    .setAction(R.string.delete_all_failure_details, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.delete_all_failure_details_dialog)
                                    .setItems(new String[]{errorMessage}, null)
                                    .show();
                        }
                    })
                    .show();
        }
    }
}
