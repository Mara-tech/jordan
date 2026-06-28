package com.mara.jordan.app.ui;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mara.jordan.app.R;
import com.mara.jordan.app.model.JordanClientModel;

public abstract class InServerFragment extends Fragment implements FullDeletionCallback {

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.server_global_menu, menu);
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
        }
        return super.onOptionsItemSelected(item);
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
