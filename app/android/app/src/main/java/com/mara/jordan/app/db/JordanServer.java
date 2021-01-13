package com.mara.jordan.app.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.Expose;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
public class JordanServer {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @Expose
    private String name;
    @Expose
    private String url;
    @Expose
    private String login;
    @Expose(serialize = false)
    private String password;

}
