package com.mara.jordan.app.model;

import android.content.Context;
import android.util.Log;

public class JordanServerModel {


    private static final String TAG = "JordanServerModel";
    private final Context context;

    public JordanServerModel(Context ctx) {
        context = ctx;
    }

    public void addServer(String serverName, String serverBaseUri, boolean rememberLogin, String login, boolean rememberPassword, String password) {
        Log.i(TAG, "server " + serverName + "added.");
    }
}
