package com.mara.jordan.app.db;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class JordanServerDaoTest {

    private JordanServerDatabase db;
    private JordanServerDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, JordanServerDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.serverDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insertAndGetAll_returnsInsertedServer() {
        JordanServer server = JordanServer.builder()
                .name("prod")
                .url("https://example.com/jordan/admin")
                .login("admin")
                .password("secret")
                .build();
        dao.insertAll(server);

        List<JordanServer> servers = dao.getAll().blockingGet();
        assertEquals(1, servers.size());
        assertEquals("prod", servers.get(0).getName());
        assertEquals("https://example.com/jordan/admin", servers.get(0).getUrl());
        assertEquals("admin", servers.get(0).getLogin());
    }

    @Test
    public void insertMultiple_getAllReturnsAll() {
        dao.insertAll(
                JordanServer.builder().name("server-a").url("http://a").build(),
                JordanServer.builder().name("server-b").url("http://b").build(),
                JordanServer.builder().name("server-c").url("http://c").build()
        );

        List<JordanServer> servers = dao.getAll().blockingGet();
        assertEquals(3, servers.size());
    }

    @Test
    public void update_reflectsNewValues() {
        JordanServer server = JordanServer.builder()
                .name("original")
                .url("http://original")
                .build();
        dao.insertAll(server);

        JordanServer inserted = dao.getAll().blockingGet().get(0);
        JordanServer updated = JordanServer.builder()
                .id(inserted.getId())
                .name("updated")
                .url("http://updated")
                .build();
        dao.updateAll(updated);

        JordanServer fetched = dao.getAll().blockingGet().get(0);
        assertEquals("updated", fetched.getName());
        assertEquals("http://updated", fetched.getUrl());
    }

    @Test
    public void delete_removesServerFromList() {
        JordanServer server = JordanServer.builder()
                .name("to-delete")
                .url("http://delete-me")
                .build();
        dao.insertAll(server);

        JordanServer inserted = dao.getAll().blockingGet().get(0);
        dao.delete(inserted);

        List<JordanServer> servers = dao.getAll().blockingGet();
        assertTrue(servers.isEmpty());
    }

    @Test
    public void emptyDatabase_getAllReturnsEmptyList() {
        List<JordanServer> servers = dao.getAll().blockingGet();
        assertTrue(servers.isEmpty());
    }
}
