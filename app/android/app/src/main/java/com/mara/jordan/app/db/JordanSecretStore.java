package com.mara.jordan.app.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Where the operator passwords the user asked this device to remember are kept.
 * <p>
 * The password never reaches the {@link JordanServer} row : the database holds the login and the
 * row id, which is the reference into this store, and the store holds the secret encrypted with a
 * key the Android Keystore never gives out. A copied database file, an ADB backup or a future CSV
 * export of the server list therefore cannot carry a password — there is none in them.
 * <p>
 * Secrets are keyed by row id rather than by URL because the URL of a server can be corrected in
 * the setup dialog, and correcting it must not lose — nor strand — its password. SQLite
 * {@code AUTOINCREMENT} ids are never reused, so a new server cannot inherit a deleted one's.
 * <p>
 * A Keystore AES key needs API 23. Below that nothing is remembered, rather than remembered in
 * clear : {@link #isAvailable()} says so, and the login dialog stops offering the choice.
 */
public final class JordanSecretStore {

    private static final String TAG = "JordanSecretStore";

    private static final String PREFERENCES_FILE = "jordan_server_secrets";
    private static final String KEY_PREFIX = "password:";

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "jordan-server-credentials";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    /** Length of the GCM nonce the cipher generates, prepended to the ciphertext it protects. */
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private static volatile JordanSecretStore INSTANCE;

    private final SharedPreferences preferences;
    private final boolean available;

    private JordanSecretStore(Context ctx) {
        preferences = ctx.getApplicationContext()
                .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && secretKey() != null;
    }

    public static JordanSecretStore getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (JordanSecretStore.class) {
                if (INSTANCE == null) {
                    INSTANCE = new JordanSecretStore(ctx);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * @return whether this device can keep a password out of reach — false on API 21 and 22,
     * where there is no Keystore AES key, and on the rare device whose Keystore refuses one.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Whether a password was remembered for this server. Reads no secret and runs no crypto,
     * so it is cheap enough for the server list.
     */
    public boolean hasSecret(int serverId) {
        return isStorable(serverId) && preferences.contains(key(serverId));
    }

    /**
     * @return the password remembered for this server, or {@code null} when there is none left.
     * A secret that cannot be decrypted any more — the Keystore key is gone, typically after a
     * restore on another device — is dropped here and asked again at the next login.
     */
    @Nullable
    public String getSecret(int serverId) {
        if (!available || !isStorable(serverId)) {
            return null;
        }
        final String stored = preferences.getString(key(serverId), null);
        if (stored == null) {
            return null;
        }
        // available already says the Keystore answered, which only happens from M on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return decrypt(stored);
            } catch (GeneralSecurityException | IllegalArgumentException error) {
                Log.w(TAG, "Unreadable secret for server " + serverId + ", forgetting it", error);
                forget(serverId);
            }
        }
        return null;
    }

    /**
     * Remembers a password for a server, or forgets it when given {@code null} or an empty one.
     */
    public void setSecret(int serverId, @Nullable String secret) {
        if (!isStorable(serverId)) {
            return;
        }
        if (secret == null || secret.isEmpty() || !available
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            forget(serverId);
            return;
        }
        try {
            preferences.edit().putString(key(serverId), encrypt(secret)).apply();
        } catch (GeneralSecurityException error) {
            Log.e(TAG, "Could not store the secret of server " + serverId, error);
            forget(serverId);
        }
    }

    /**
     * Drops the secret of a server, called when the user stops remembering it and when the
     * server itself is deleted — a forgotten server must not leave a password behind.
     */
    public void forget(int serverId) {
        if (isStorable(serverId)) {
            preferences.edit().remove(key(serverId)).apply();
        }
    }

    /**
     * A row Room has not inserted yet has no id, so nothing can be attached to it.
     */
    private static boolean isStorable(int serverId) {
        return serverId > 0;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private String encrypt(String secret) throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        final byte[] iv = cipher.getIV();
        final byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        final byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return Base64.encodeToString(payload, Base64.NO_WRAP);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private String decrypt(String stored) throws GeneralSecurityException {
        final byte[] payload = Base64.decode(stored, Base64.NO_WRAP);
        if (payload.length <= GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Stored secret is too short to hold an IV");
        }
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload, 0, GCM_IV_LENGTH_BYTES));
        final byte[] clear = cipher.doFinal(payload, GCM_IV_LENGTH_BYTES,
                payload.length - GCM_IV_LENGTH_BYTES);
        return new String(clear, StandardCharsets.UTF_8);
    }

    /**
     * The app key, generated on first use and kept by the Keystore, which hands out operations
     * on it but never the key material itself.
     */
    @Nullable
    private SecretKey secretKey() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        try {
            final KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            final KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            return generateKey();
        } catch (GeneralSecurityException | IOException error) {
            Log.e(TAG, "No Keystore key available, credentials will not be remembered", error);
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private SecretKey generateKey() throws GeneralSecurityException {
        final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String key(int serverId) {
        return KEY_PREFIX + serverId;
    }
}
