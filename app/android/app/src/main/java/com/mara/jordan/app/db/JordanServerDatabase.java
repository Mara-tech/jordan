package com.mara.jordan.app.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {JordanServer.class}, version = 1)
public abstract class JordanServerDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "JordanServerDatabase";
    private static JordanServerDatabase INSTANCE;

    public abstract JordanServerDao serverDao();

    public static JordanServerDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (JordanServerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(),
                            JordanServerDatabase.class, JordanServerDatabase.DATABASE_NAME).build();
                }
            }
        }
        return INSTANCE;
    }

}
