package com.mara.jordan.app.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.Single;

@Dao
public interface JordanServerDao {
    @Query("SELECT * FROM JordanServer")
    Single<List<JordanServer>> getAll();

    @Insert
    void insertAll(JordanServer... servers);

    @Update
    void updateAll(JordanServer... servers);

    @Delete
    void delete(JordanServer server);

}
