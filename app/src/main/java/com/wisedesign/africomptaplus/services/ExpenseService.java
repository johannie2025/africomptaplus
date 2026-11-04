package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Expense;

import java.util.ArrayList;
import java.util.List;

public class ExpenseService {

    private final DatabaseHelper db;

    public ExpenseService(Context context) {
        this.db = DatabaseHelper.getInstance(context);
    }

    public long save(Expense e) {
        ContentValues cv = toContentValues(e);
        if (e.id == 0) {
            return db.insert(DatabaseHelper.T_EXPENSES, cv);
        } else {
            db.update(DatabaseHelper.T_EXPENSES, cv,
                    DatabaseHelper.COL_ID + "=?", new String[]{String.valueOf(e.id)});
            return e.id;
        }
    }

    public boolean delete(long id) {
        return db.delete(DatabaseHelper.T_EXPENSES,
                DatabaseHelper.COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<Expense> findAll() {
        List<Expense> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_EXPENSES
                + " ORDER BY " + DatabaseHelper.COL_DATE + " DESC", null);
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally { c.close(); }
        return list;
    }

    public List<Expense> findByPeriod(String start, String end) {
        List<Expense> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_EXPENSES
                + " WHERE " + DatabaseHelper.COL_DATE + " BETWEEN ? AND ?"
                + " ORDER BY " + DatabaseHelper.COL_DATE + " DESC",
                new String[]{start, end});
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally { c.close(); }
        return list;
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private ContentValues toContentValues(Expense e) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_CATEGORY,    e.category);
        cv.put(DatabaseHelper.COL_DESCRIPTION, e.description);
        cv.put(DatabaseHelper.COL_AMOUNT,      e.amount);
        cv.put(DatabaseHelper.COL_DATE,        e.date);
        return cv;
    }

    private Expense fromCursor(Cursor c) {
        Expense e = new Expense();
        e.id          = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        e.category    = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CATEGORY));
        e.description = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_DESCRIPTION));
        e.amount      = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_AMOUNT));
        e.date        = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_DATE));
        return e;
    }
}
