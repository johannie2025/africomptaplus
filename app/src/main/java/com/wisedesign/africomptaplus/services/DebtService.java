package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Debt;
import java.text.SimpleDateFormat;
import java.util.*;

public class DebtService {
    private final DatabaseHelper db;
    public DebtService(Context ctx) { db = DatabaseHelper.getInstance(ctx); }

    /** Crée une dette liée à une vente. */
    public long createDebt(long clientId, long saleId, double amount, String dueDate, String notes) {
        String now = now();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_CLIENT_ID,   clientId);
        cv.put(DatabaseHelper.COL_SALE_ID,     saleId);
        cv.put(DatabaseHelper.COL_AMOUNT,      amount);
        cv.put(DatabaseHelper.COL_PAID_AMOUNT, 0.0);
        cv.put(DatabaseHelper.COL_DUE_DATE,    dueDate != null ? dueDate : "");
        cv.put(DatabaseHelper.COL_STATUS,      "open");
        cv.put(DatabaseHelper.COL_NOTES,       notes != null ? notes : "");
        cv.put(DatabaseHelper.COL_CREATED_AT,  now);
        cv.put(DatabaseHelper.COL_UPDATED_AT,  now);
        return db.insert(DatabaseHelper.T_DEBTS, cv);
    }

    /** Compat : paiement espèces sans note. */
    public void recordPayment(long debtId, double paymentAmount) { recordPayment(debtId, paymentAmount, "cash", ""); }

    /** Enregistre un paiement (plafonné au restant dû) et l'historise. Retourne l'id du paiement, -1 si refusé. */
    public long recordPayment(long debtId, double paymentAmount, String method, String note) {
        Debt debt = findById(debtId);
        if (debt == null || paymentAmount <= 0) return -1;
        double remaining = debt.amount - debt.paidAmount;
        if (remaining <= 0) return -1;
        double pay = Math.min(paymentAmount, remaining);
        double newPaid = debt.paidAmount + pay;
        String status = newPaid >= debt.amount ? "paid" : "partial";
        String now = now();
        android.database.sqlite.SQLiteDatabase sq = db.getWritableDatabase();
        sq.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(DatabaseHelper.COL_PAID_AMOUNT, newPaid);
            cv.put(DatabaseHelper.COL_STATUS,      status);
            cv.put(DatabaseHelper.COL_UPDATED_AT,  now);
            sq.update(DatabaseHelper.T_DEBTS, cv, DatabaseHelper.COL_ID + "=?", new String[]{String.valueOf(debtId)});
            ContentValues pc = new ContentValues();
            pc.put("debt_id", debtId);
            pc.put("amount", pay);
            pc.put("method", method != null ? method : "cash");
            pc.put("note", note != null ? note : "");
            pc.put("created_at", now);
            long id = sq.insert(DatabaseHelper.T_DEBT_PAYMENTS, null, pc);
            sq.setTransactionSuccessful();
            return id;
        } finally { sq.endTransaction(); }
    }

    public java.util.List<com.wisedesign.africomptaplus.models.DebtPayment> findPayments(long debtId) {
        java.util.List<com.wisedesign.africomptaplus.models.DebtPayment> list = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_DEBT_PAYMENTS + " WHERE debt_id=? ORDER BY id DESC",
                new String[]{String.valueOf(debtId)});
        try { while (c.moveToNext()) list.add(payment(c)); } finally { c.close(); }
        return list;
    }

    public com.wisedesign.africomptaplus.models.DebtPayment findPayment(long id) {
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_DEBT_PAYMENTS + " WHERE id=?", new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? payment(c) : null; } finally { c.close(); }
    }

    private com.wisedesign.africomptaplus.models.DebtPayment payment(Cursor c) {
        com.wisedesign.africomptaplus.models.DebtPayment p = new com.wisedesign.africomptaplus.models.DebtPayment();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.debtId = c.getLong(c.getColumnIndexOrThrow("debt_id"));
        p.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        p.method = c.getString(c.getColumnIndexOrThrow("method"));
        p.note = c.getString(c.getColumnIndexOrThrow("note"));
        p.createdAt = c.getString(c.getColumnIndexOrThrow("created_at"));
        return p;
    }

    /** Somme payée jusqu'à (et y compris) ce paiement — pour le solde affiché sur le reçu. */
    public double paidUpTo(long debtId, long paymentId) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM " + DatabaseHelper.T_DEBT_PAYMENTS + " WHERE debt_id=? AND id<=?",
                new String[]{String.valueOf(debtId), String.valueOf(paymentId)});
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    /** Toutes les dettes, soldées incluses (pour consulter l'historique). */
    public List<Debt> findAllIncludingPaid() {
        String sql = "SELECT d.*, c." + DatabaseHelper.COL_NAME + " AS client_name"
                + " FROM " + DatabaseHelper.T_DEBTS + " d"
                + " LEFT JOIN " + DatabaseHelper.T_CLIENTS + " c ON c." + DatabaseHelper.COL_ID + "=d." + DatabaseHelper.COL_CLIENT_ID
                + " ORDER BY d." + DatabaseHelper.COL_CREATED_AT + " DESC";
        return fetchList(sql, null);
    }

    public Debt findById(long id) {
        String sql = "SELECT d.*, c." + DatabaseHelper.COL_NAME + " AS client_name"
                + " FROM " + DatabaseHelper.T_DEBTS + " d"
                + " LEFT JOIN " + DatabaseHelper.T_CLIENTS + " c ON c." + DatabaseHelper.COL_ID + "=d." + DatabaseHelper.COL_CLIENT_ID
                + " WHERE d." + DatabaseHelper.COL_ID + "=? LIMIT 1";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? fromCursor(c) : null; } finally { c.close(); }
    }

    /** Toutes les dettes actives (open + partial). */
    public List<Debt> findAllOpen() {
        String sql = "SELECT d.*, c." + DatabaseHelper.COL_NAME + " AS client_name"
                + " FROM " + DatabaseHelper.T_DEBTS + " d"
                + " LEFT JOIN " + DatabaseHelper.T_CLIENTS + " c ON c." + DatabaseHelper.COL_ID + "=d." + DatabaseHelper.COL_CLIENT_ID
                + " WHERE d." + DatabaseHelper.COL_STATUS + " != 'paid'"
                + " ORDER BY d." + DatabaseHelper.COL_CREATED_AT + " DESC";
        return fetchList(sql, null);
    }

    /** Dettes d'un client précis. */
    public List<Debt> findByClient(long clientId) {
        String sql = "SELECT d.*, c." + DatabaseHelper.COL_NAME + " AS client_name"
                + " FROM " + DatabaseHelper.T_DEBTS + " d"
                + " LEFT JOIN " + DatabaseHelper.T_CLIENTS + " c ON c." + DatabaseHelper.COL_ID + "=d." + DatabaseHelper.COL_CLIENT_ID
                + " WHERE d." + DatabaseHelper.COL_CLIENT_ID + "=?"
                + " ORDER BY d." + DatabaseHelper.COL_CREATED_AT + " DESC";
        return fetchList(sql, new String[]{String.valueOf(clientId)});
    }

    /** Total des dettes non payées. */
    public double totalOutstanding() {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + " - " + DatabaseHelper.COL_PAID_AMOUNT + "),0)"
                + " FROM " + DatabaseHelper.T_DEBTS + " WHERE " + DatabaseHelper.COL_STATUS + " != 'paid'", null);
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    private List<Debt> fetchList(String sql, String[] args) {
        List<Debt> list = new ArrayList<>();
        Cursor c = db.rawQuery(sql, args);
        try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
        return list;
    }

    private Debt fromCursor(Cursor c) {
        Debt d = new Debt();
        d.id          = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        d.clientId    = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_CLIENT_ID));
        d.saleId      = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_SALE_ID));
        d.amount      = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_AMOUNT));
        d.paidAmount  = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_PAID_AMOUNT));
        d.dueDate     = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_DUE_DATE));
        d.status      = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS));
        d.notes       = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_NOTES));
        d.createdAt   = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT));
        d.updatedAt   = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_UPDATED_AT));
        int ciIdx     = c.getColumnIndex("client_name");
        if (ciIdx >= 0) d.clientName = c.getString(ciIdx);
        return d;
    }

    private String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }
}
