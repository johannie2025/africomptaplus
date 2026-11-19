package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Client;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClientService {
    private final DatabaseHelper db;
    public ClientService(Context ctx) { db = DatabaseHelper.getInstance(ctx); }

    public long save(Client c) {
        if (c.createdAt == null) c.createdAt = now();
        ContentValues cv = toCV(c);
        if (c.id == 0) return db.insert(DatabaseHelper.T_CLIENTS, cv);
        db.update(DatabaseHelper.T_CLIENTS, cv, DatabaseHelper.COL_ID + "=?", new String[]{String.valueOf(c.id)});
        return c.id;
    }

    /** Supprime ; avec ventes/dettes/devis, le client est archivé (masqué) pour conserver l'historique. */
    public boolean delete(long id) {
        String[] a = {String.valueOf(id)};
        boolean used = false;
        for (String q : new String[]{"SELECT 1 FROM sales WHERE client_id=? LIMIT 1", "SELECT 1 FROM debts WHERE client_id=? LIMIT 1", "SELECT 1 FROM documents WHERE client_id=? LIMIT 1"}) {
            Cursor c = db.rawQuery(q, a); try { if (c.moveToFirst()) used = true; } finally { c.close(); }
        }
        if (used) { db.getWritableDatabase().execSQL("UPDATE " + DatabaseHelper.T_CLIENTS + " SET archived=1 WHERE id=?", a); return true; }
        return db.delete(DatabaseHelper.T_CLIENTS, DatabaseHelper.COL_ID + "=?", a) > 0;
    }

    public Client findById(long id) {
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_CLIENTS + " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1", new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? fromCursor(c) : null; } finally { c.close(); }
    }

    public List<Client> findAll() {
        List<Client> list = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_CLIENTS + " WHERE archived=0 ORDER BY " + DatabaseHelper.COL_NAME + " ASC", null);
        try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<Client> search(String q) {
        List<Client> list = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_CLIENTS
                + " WHERE archived=0 AND (" + DatabaseHelper.COL_NAME + " LIKE ? OR " + DatabaseHelper.COL_PHONE + " LIKE ?)"
                + " ORDER BY " + DatabaseHelper.COL_NAME + " ASC",
                new String[]{"%" + q + "%", "%" + q + "%"});
        try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
        return list;
    }

    private ContentValues toCV(Client c) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_NAME,    c.name);
        cv.put(DatabaseHelper.COL_PHONE,   c.phone);
        cv.put(DatabaseHelper.COL_EMAIL,   c.email);
        cv.put(DatabaseHelper.COL_ADDRESS, c.address);
        cv.put(DatabaseHelper.COL_NOTES,   c.notes);
        cv.put(DatabaseHelper.COL_CREATED_AT, c.createdAt);
        return cv;
    }

    private Client fromCursor(Cursor c) {
        Client cl = new Client();
        cl.id        = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        cl.name      = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_NAME));
        cl.phone     = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_PHONE));
        cl.email     = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_EMAIL));
        cl.address   = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_ADDRESS));
        cl.notes     = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_NOTES));
        cl.createdAt = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT));
        return cl;
    }

    private String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }
}
