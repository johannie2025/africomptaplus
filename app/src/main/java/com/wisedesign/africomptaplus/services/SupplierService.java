package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Purchase;
import com.wisedesign.africomptaplus.models.SaleItem;
import com.wisedesign.africomptaplus.models.Supplier;

import java.text.SimpleDateFormat;
import java.util.*;

/** Fournisseurs, achats (entrée en stock) et règlements. La comptabilité est dérivée dans AccountingService. */
public class SupplierService {
    private final DatabaseHelper db;
    public SupplierService(Context c) { db = DatabaseHelper.getInstance(c); }

    private static String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }

    // ── Fournisseurs ────────────────────────────────────────────────────────
    public long save(Supplier s) {
        ContentValues cv = new ContentValues();
        cv.put("name", s.name); cv.put("phone", s.phone); cv.put("email", s.email);
        cv.put("address", s.address); cv.put("notes", s.notes);
        if (s.id > 0) { db.update(DatabaseHelper.T_SUPPLIERS, cv, "id=?", new String[]{String.valueOf(s.id)}); return s.id; }
        cv.put("created_at", now());
        return db.insert(DatabaseHelper.T_SUPPLIERS, cv);
    }

    /** false si le fournisseur a des achats. */
    public boolean delete(long id) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_PURCHASES + " WHERE supplier_id=?", new String[]{String.valueOf(id)});
        try { if (c.moveToFirst() && c.getInt(0) > 0) return false; } finally { c.close(); }
        db.delete(DatabaseHelper.T_SUPPLIERS, "id=?", new String[]{String.valueOf(id)});
        return true;
    }

    public List<Supplier> list(String q) {
        List<Supplier> out = new ArrayList<>();
        String l = "%" + (q == null ? "" : q.trim()) + "%";
        Cursor c = db.rawQuery("SELECT s.*, COALESCE(SUM(p.total_amount - p.paid_amount),0) AS due, COUNT(p.id) AS n"
                + " FROM suppliers s LEFT JOIN purchases p ON p.supplier_id=s.id"
                + " WHERE s.name LIKE ? OR s.phone LIKE ? GROUP BY s.id ORDER BY s.name COLLATE NOCASE", new String[]{l, l});
        try { while (c.moveToNext()) out.add(supplier(c)); } finally { c.close(); }
        return out;
    }

    public Supplier find(long id) {
        Cursor c = db.rawQuery("SELECT s.*, COALESCE(SUM(p.total_amount - p.paid_amount),0) AS due, COUNT(p.id) AS n"
                + " FROM suppliers s LEFT JOIN purchases p ON p.supplier_id=s.id WHERE s.id=? GROUP BY s.id", new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? supplier(c) : null; } finally { c.close(); }
    }

    private Supplier supplier(Cursor c) {
        Supplier s = new Supplier();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.phone = nz(c.getString(c.getColumnIndexOrThrow("phone")));
        s.email = nz(c.getString(c.getColumnIndexOrThrow("email")));
        s.address = nz(c.getString(c.getColumnIndexOrThrow("address")));
        s.notes = nz(c.getString(c.getColumnIndexOrThrow("notes")));
        s.due = c.getDouble(c.getColumnIndexOrThrow("due"));
        s.purchaseCount = c.getInt(c.getColumnIndexOrThrow("n"));
        return s;
    }
    private static String nz(String s) { return s == null ? "" : s; }

    public double totalPayable() {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(total_amount - paid_amount),0) FROM " + DatabaseHelper.T_PURCHASES, null);
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    // ── Achats ──────────────────────────────────────────────────────────────
    /**
     * Enregistre l'achat : stock += quantité, prix d'achat mis à jour si demandé,
     * acompte (initialPaid) réglé immédiatement par `method`, le reste est dû au fournisseur.
     * @return id de l'achat ou -1
     */
    public long createPurchase(Supplier s, List<SaleItem> items, double initialPaid, String method, String notes, boolean updateCost) {
        if (s == null || items == null || items.isEmpty()) return -1;
        double total = 0;
        for (SaleItem i : items) total += i.quantity * i.unitPrice;
        double paid = Math.max(0, Math.min(initialPaid, total));
        SQLiteDatabase sq = db.getWritableDatabase();
        sq.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("reference", "ACH-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()));
            cv.put("supplier_id", s.id); cv.put("supplier_name", s.name);
            cv.put("total_amount", total); cv.put("initial_paid", paid); cv.put("paid_amount", paid);
            cv.put("payment_method", method == null ? "cash" : method);
            cv.put("notes", notes == null ? "" : notes); cv.put("created_at", now());
            long id = sq.insert(DatabaseHelper.T_PURCHASES, null, cv);
            if (id < 0) return -1;
            for (SaleItem i : items) {
                ContentValues ic = new ContentValues();
                ic.put("purchase_id", id); ic.put("product_id", i.productId); ic.put("product_name", i.productName);
                ic.put("quantity", i.quantity); ic.put("unit_cost", i.unitPrice); ic.put("total_cost", i.quantity * i.unitPrice);
                sq.insert(DatabaseHelper.T_PURCHASE_ITEMS, null, ic);
                sq.execSQL("UPDATE " + DatabaseHelper.T_PRODUCTS + " SET " + DatabaseHelper.COL_STOCK + "=" + DatabaseHelper.COL_STOCK + "+? WHERE id=?",
                        new Object[]{i.quantity, i.productId});
                if (updateCost) sq.execSQL("UPDATE " + DatabaseHelper.T_PRODUCTS + " SET " + DatabaseHelper.COL_BUYING_PRICE + "=? WHERE id=?",
                        new Object[]{i.unitPrice, i.productId});
            }
            sq.setTransactionSuccessful();
            return id;
        } finally { sq.endTransaction(); }
    }

    public List<Purchase> purchases(long supplierId, String q, String from, String to) {
        List<Purchase> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM purchases WHERE 1=1");
        List<String> a = new ArrayList<>();
        if (supplierId > 0) { sql.append(" AND supplier_id=?"); a.add(String.valueOf(supplierId)); }
        if (q != null && !q.trim().isEmpty()) { sql.append(" AND (reference LIKE ? OR supplier_name LIKE ? OR notes LIKE ?)"); String l = "%" + q.trim() + "%"; a.add(l); a.add(l); a.add(l); }
        if (from != null && !from.isEmpty()) { sql.append(" AND date(created_at)>=date(?)"); a.add(from); }
        if (to != null && !to.isEmpty()) { sql.append(" AND date(created_at)<=date(?)"); a.add(to); }
        sql.append(" ORDER BY created_at DESC");
        Cursor c = db.rawQuery(sql.toString(), a.toArray(new String[0]));
        try { while (c.moveToNext()) out.add(purchase(c)); } finally { c.close(); }
        return out;
    }

    public Purchase findPurchase(long id) {
        Cursor c = db.rawQuery("SELECT * FROM purchases WHERE id=?", new String[]{String.valueOf(id)});
        Purchase p = null;
        try { if (c.moveToFirst()) p = purchase(c); } finally { c.close(); }
        if (p == null) return null;
        Cursor ic = db.rawQuery("SELECT * FROM purchase_items WHERE purchase_id=? ORDER BY id", new String[]{String.valueOf(id)});
        try {
            while (ic.moveToNext()) {
                SaleItem i = new SaleItem();
                i.productId = ic.getLong(ic.getColumnIndexOrThrow("product_id"));
                i.productName = ic.getString(ic.getColumnIndexOrThrow("product_name"));
                i.quantity = ic.getInt(ic.getColumnIndexOrThrow("quantity"));
                i.unitPrice = ic.getDouble(ic.getColumnIndexOrThrow("unit_cost"));
                i.totalPrice = ic.getDouble(ic.getColumnIndexOrThrow("total_cost"));
                p.items.add(i);
            }
        } finally { ic.close(); }
        return p;
    }

    private Purchase purchase(Cursor c) {
        Purchase p = new Purchase();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.reference = c.getString(c.getColumnIndexOrThrow("reference"));
        p.supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id"));
        p.supplierName = c.getString(c.getColumnIndexOrThrow("supplier_name"));
        p.total = c.getDouble(c.getColumnIndexOrThrow("total_amount"));
        p.initialPaid = c.getDouble(c.getColumnIndexOrThrow("initial_paid"));
        p.paid = c.getDouble(c.getColumnIndexOrThrow("paid_amount"));
        p.paymentMethod = c.getString(c.getColumnIndexOrThrow("payment_method"));
        p.notes = nz(c.getString(c.getColumnIndexOrThrow("notes")));
        p.createdAt = c.getString(c.getColumnIndexOrThrow("created_at"));
        return p;
    }

    // ── Règlements ──────────────────────────────────────────────────────────
    /** Règle une partie (plafonnée au reste dû). Retourne le montant réellement enregistré, 0 si refusé. */
    public double pay(long purchaseId, double amount, String method, String note) {
        Purchase p = findPurchase(purchaseId);
        if (p == null || amount <= 0) return 0;
        double pay = Math.min(amount, p.remaining());
        if (pay <= 0) return 0;
        SQLiteDatabase sq = db.getWritableDatabase();
        sq.beginTransaction();
        try {
            ContentValues pc = new ContentValues();
            pc.put("purchase_id", purchaseId); pc.put("supplier_id", p.supplierId); pc.put("amount", pay);
            pc.put("method", method == null ? "cash" : method); pc.put("note", note == null ? "" : note); pc.put("created_at", now());
            sq.insert(DatabaseHelper.T_SUPPLIER_PAYMENTS, null, pc);
            sq.execSQL("UPDATE " + DatabaseHelper.T_PURCHASES + " SET paid_amount=paid_amount+? WHERE id=?", new Object[]{pay, purchaseId});
            sq.setTransactionSuccessful();
            return pay;
        } finally { sq.endTransaction(); }
    }

    /** Lignes « date · mode · montant » des règlements d'un achat. */
    public List<String> paymentLines(long purchaseId) {
        List<String> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT created_at, method, amount, note FROM supplier_payments WHERE purchase_id=? ORDER BY id", new String[]{String.valueOf(purchaseId)});
        try {
            while (c.moveToNext())
                out.add(c.getString(0) + " · " + ("mobile_money".equals(c.getString(1)) ? "Mobile Money" : "Espèces") + " · "
                        + com.wisedesign.africomptaplus.ui.UiKit.xaf(c.getDouble(2)) + (c.getString(3) != null && !c.getString(3).isEmpty() ? " (" + c.getString(3) + ")" : ""));
        } finally { c.close(); }
        return out;
    }
}
