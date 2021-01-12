package com.mara.jordan.app.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

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

    private String name;
    private String url;
    private String login;
    private String password;

}
