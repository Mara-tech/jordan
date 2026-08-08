package com.mara.jordan.app.db;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {JordanServer.class}, version = 2, exportSchema = false)
public abstract class JordanServerDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "JordanServerDatabase";
    private static final String TAG = "JordanServerDatabase";
    private static JordanServerDatabase INSTANCE;

    public abstract JordanServerDao serverDao();

    public static JordanServerDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (JordanServerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(),
                            JordanServerDatabase.class, JordanServerDatabase.DATABASE_NAME)
                            .addMigrations(secretsOutOfTheDatabase(ctx.getApplicationContext()))
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Version 1 kept the operator password in clear in the {@code password} column. Version 2
     * drops the column : the passwords already remembered move to {@link JordanSecretStore},
     * which encrypts them with a Keystore key, and are simply lost — and asked again at the next
     * login — on a device that has no such key.
     */
    static Migration secretsOutOfTheDatabase(Context ctx) {
        return new Migration(1, 2) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                rescueRememberedPasswords(ctx, database);
                // SQLite gained DROP COLUMN too late to be relied on : the table is rebuilt
                database.execSQL("CREATE TABLE IF NOT EXISTS `JordanServer_new` "
                        + "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `url` TEXT, `login` TEXT)");
                database.execSQL("INSERT INTO `JordanServer_new` (`id`, `name`, `url`, `login`) "
                        + "SELECT `id`, `name`, `url`, `login` FROM `JordanServer`");
                database.execSQL("DROP TABLE `JordanServer`");
                database.execSQL("ALTER TABLE `JordanServer_new` RENAME TO `JordanServer`");
            }
        };
    }

    private static void rescueRememberedPasswords(Context ctx, SupportSQLiteDatabase database) {
        final JordanSecretStore secrets = JordanSecretStore.getInstance(ctx);
        if (!secrets.isAvailable()) {
            return;
        }
        try (Cursor cursor = database.query(
                "SELECT `id`, `password` FROM `JordanServer` WHERE `password` IS NOT NULL")) {
            while (cursor.moveToNext()) {
                secrets.setSecret(cursor.getInt(0), cursor.getString(1));
            }
        } catch (RuntimeException error) {
            // a password not carried over costs one login, a failed migration costs the server list
            Log.e(TAG, "Could not move the remembered passwords to the secret store", error);
        }
    }

}
