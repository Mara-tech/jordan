package com.mara.jordan.app.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.Expose;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * A Jordan server the user declared on this device. Nothing here is secret : the password of
 * {@link #login}, when the user asked to remember it, lives in {@link JordanSecretStore} and
 * never in this row — so a server list can be exported, backed up or read from the database
 * file without carrying it.
 */
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
    /** Operator account remembered for this server, and the reference into {@link JordanSecretStore}. */
    @Expose
    private String login;

}
