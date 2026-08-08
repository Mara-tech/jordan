package com.mara.jordan.app.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.Single;

@Dao
public interface JordanServerDao {
    @Query("SELECT * FROM JordanServer")
    Single<List<JordanServer>> getAll();

    /**
     * The server the app is currently talking to, to recover the credentials the user chose
     * to remember. Empty when the URL is unknown, which is not an error.
     */
    @Query("SELECT * FROM JordanServer WHERE url = :url LIMIT 1")
    Maybe<JordanServer> findByUrl(String url);

    @Insert
    void insertAll(JordanServer... servers);

    @Update
    void updateAll(JordanServer... servers);

    @Delete
    void delete(JordanServer server);

}
