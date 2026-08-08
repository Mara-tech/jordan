package com.mara.jordan.app.model;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mara.jordan.app.api.JordanApi;
import com.mara.jordan.app.api.JordanGetClientsCallback;
import com.mara.jordan.app.api.JordanLoginCallback;
import com.mara.jordan.app.db.JordanFindServerCallback;
import com.mara.jordan.app.db.JordanListServersCallback;
import com.mara.jordan.app.db.JordanSecretStore;
import com.mara.jordan.app.db.JordanServer;
import com.mara.jordan.app.db.JordanServerDao;
import com.mara.jordan.app.db.JordanServerDatabase;
import com.mara.jordan.app.db.OnServerUpdateListener;
import com.mara.jordan.app.ui.ServerConnectionTestCallback;
import com.mara.jordan.app.utils.NetworkUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class JordanServerModel implements JordanModel {


    private static final String TAG = "JordanServerModel";
    private final Context context;
    private final JordanServerDao serverDao;
    private final JordanSecretStore secretStore;
    private final JordanApi api;

    public JordanServerModel(Context ctx) {
        context = ctx;
        serverDao = JordanServerDatabase.getInstance(ctx).serverDao();
        secretStore = JordanSecretStore.getInstance(ctx);
        api = JordanApi.getInstance(context);
    }

    public void listServers(JordanListServersCallback... callbacks) {
        Single<List<JordanServer>> single = serverDao.getAll();
        single
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        response -> handleSuccess(response, callbacks),
                        error -> handleError(error, callbacks))
        ;
    }

    public void addServers(JordanServer[] entities, OnServerUpdateListener callback) {
        Completable.fromAction(() -> serverDao.insertAll(entities))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> callback.onServerAdded(entities),
                        error -> callback.onServerUpdateError(error, entities)
                );
    }

    public void addServer(JordanServer entity, OnServerUpdateListener callback) {
        addServers(new JordanServer[]{entity}, callback);
    }

    private void handleSuccess(List<JordanServer> response, JordanListServersCallback[] callbacks) {
        List<JordanServer> safeResponse = response != null ? response : ImmutableList.of();
        for(JordanListServersCallback callback : callbacks){
            callback.onServersLoaded(safeResponse);
        }
    }

    private void handleError(Throwable error, JordanListServersCallback[] callbacks) {
        Throwable nonNullError = error != null ? error : new Throwable("Unknown error while listing servers");
        for(JordanListServersCallback callback : callbacks){
            callback.onServersLoadingError(nonNullError);
        }
    }

    public void delete(JordanServer server, OnServerUpdateListener callback) {
        Completable.fromAction(() -> {
                    serverDao.delete(server);
                    // a server the user dropped must not leave its password behind
                    secretStore.forget(server.getId());
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> callback.onServerDeleted(server),
                        error -> callback.onServerUpdateError(error, server)
                );
    }

    public void update(JordanServer server, OnServerUpdateListener callback) {
        Completable.fromAction(() -> serverDao.updateAll(server))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> callback.onServerUpdated(server),
                        error -> callback.onServerUpdateError(error, server)
                );
    }

    public void getServerInfo(JordanServer server, JordanGetClientsCallback... callbacks) {
        api.listClients(server, callbacks);
    }

    public void testConnection(String serverBaseUrl, ServerConnectionTestCallback... callbacks) {
        api.testConnection(serverBaseUrl, callbacks);
    }

    /**
     * Open an admin session with the credentials remembered for this server.
     */
    public void login(JordanServer server, JordanLoginCallback... callbacks) {
        api.login(server.getUrl(), server.getLogin(), rememberedPassword(server), callbacks);
    }

    /**
     * Store — or drop, with {@code null} arguments — the credentials this device is allowed to
     * keep for a server, as decided in the login dialog. The login goes to the database, the
     * password to the Keystore-backed store, never the other way round.
     */
    public void rememberCredentials(JordanServer server, String login, String password) {
        server.setLogin(login);
        Completable.fromAction(() -> {
                    serverDao.updateAll(server);
                    secretStore.setSecret(server.getId(), password);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> {},
                        error -> Log.e(TAG, "Could not save the credentials of " + server.getName(), error)
                );
    }

    /**
     * @return the password remembered for this server, {@code null} when there is none — because
     * the user never asked for it, or because this device cannot keep one safely.
     */
    @Nullable
    public String rememberedPassword(JordanServer server) {
        return server != null ? secretStore.getSecret(server.getId()) : null;
    }

    /**
     * Whether entering this server can open its session without asking anything : both halves of
     * the credentials must still be there, each in its own store.
     */
    public boolean hasRememberedCredentials(JordanServer server) {
        return server != null
                && StringUtils.isNotEmpty(server.getLogin())
                && secretStore.hasSecret(server.getId());
    }

    /**
     * Whether this device is able to remember a password at all — the login dialog only offers
     * the choice when it is.
     */
    public boolean canRememberCredentials() {
        return secretStore.isAvailable();
    }

    public void findServer(String serverBaseUrl, JordanFindServerCallback callback) {
        serverDao.findByUrl(NetworkUtils.removeEndingSlash(serverBaseUrl))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        callback::onServerFound,
                        error -> callback.onServerFound(null),
                        () -> callback.onServerFound(null)
                );
    }
}
