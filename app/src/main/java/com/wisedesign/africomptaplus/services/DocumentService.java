package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Document;
import com.wisedesign.africomptaplus.models.SaleItem;

import java.text.SimpleDateFormat;
import java.util.*;

/** Devis / factures proforma. Aucun mouvement de stock avant conversion en facture. */
public class DocumentService {
    private final DatabaseHelper db;
    private final SaleService sales;
    public DocumentService(Context ctx) { db = DatabaseHelper.getInstance(ctx); sales = new SaleService(ctx); }

    public long create(String type, List<SaleItem> items, long clientId, String clientName, String clientPhone, int validDays) {
        if (items == null || items.isEmpty()) return -1;
        double subtotal = 0;
        for (SaleItem i : items) subtotal += i.totalPrice;
        double rate = sales.taxRate();
        double tax = Math.round(subtotal * rate / 100.0);
        Calendar cal = Calendar.getInstance();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(cal.getTime());
        String num = (Document.PROFORMA.equals(type) ? "PRO-" : "DEV-") + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, validDays);
        String valid = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());

        SQLiteDatabase sq = db.getWritableDatabase();
        sq.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("doc_type", type); cv.put("number", num);
            cv.put("client_id", clientId);
            cv.put("client_name", clientName != null ? clientName : "Client Passager");
            cv.put("client_phone", clientPhone != null ? clientPhone : "");
            cv.put("subtotal", subtotal); cv.put("tax_rate", rate); cv.put("tax_amount", tax);
            cv.put("total_amount", subtotal + tax);
            cv.put("valid_until", valid); cv.put("status", "open"); cv.put("created_at", now);
            long id = sq.insert(DatabaseHelper.T_DOCUMENTS, null, cv);
            if (id < 0) return -1;
            for (SaleItem i : items) {
                ContentValues ic = new ContentValues();
                ic.put("document_id", id); ic.put("product_id", i.productId); ic.put("product_name", i.productName);
                ic.put("quantity", i.quantity); ic.put("unit_price", i.unitPrice); ic.put("total_price", i.totalPrice);
                sq.insert(DatabaseHelper.T_DOC_ITEMS, null, ic);
            }
            sq.setTransactionSuccessful();
            return id;
        } finally { sq.endTransaction(); }
    }

    public List<Document> search(String q, String type, String from, String to) {
        List<Document> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + DatabaseHelper.T_DOCUMENTS + " WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (type != null && !type.isEmpty()) { sql.append(" AND doc_type=?"); args.add(type); }
        if (q != null && !q.trim().isEmpty()) {
            sql.append(" AND (number LIKE ? OR client_name LIKE ? OR client_phone LIKE ?)");
            String l = "%" + q.trim() + "%"; args.add(l); args.add(l); args.add(l);
        }
        if (from != null && !from.isEmpty()) { sql.append(" AND date(created_at)>=date(?)"); args.add(from); }
        if (to   != null && !to.isEmpty())   { sql.append(" AND date(created_at)<=date(?)"); args.add(to); }
        sql.append(" ORDER BY created_at DESC");
        Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]));
        try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
        return list;
    }

    public Document findById(long id) {
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_DOCUMENTS + " WHERE id=?", new String[]{String.valueOf(id)});
        Document d = null;
        try { if (c.moveToFirst()) d = fromCursor(c); } finally { c.close(); }
        if (d == null) return null;
        Cursor ic = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_DOC_ITEMS + " WHERE document_id=? ORDER BY id", new String[]{String.valueOf(id)});
        try {
            while (ic.moveToNext()) {
                SaleItem i = new SaleItem();
                i.productId   = ic.getLong(ic.getColumnIndexOrThrow("product_id"));
                i.productName = ic.getString(ic.getColumnIndexOrThrow("product_name"));
                i.quantity    = ic.getInt(ic.getColumnIndexOrThrow("quantity"));
                i.unitPrice   = ic.getDouble(ic.getColumnIndexOrThrow("unit_price"));
                i.totalPrice  = ic.getDouble(ic.getColumnIndexOrThrow("total_price"));
                d.items.add(i);
            }
        } finally { ic.close(); }
        return d;
    }

    public void delete(long id) {
        db.delete(DatabaseHelper.T_DOC_ITEMS, "document_id=?", new String[]{String.valueOf(id)});
        db.delete(DatabaseHelper.T_DOCUMENTS, "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * Convertit en facture (vente réelle : stock décrémenté, dette si crédit).
     * @return id de la vente, ou -1 avec le message dans err[0].
     */
    public long convert(long docId, String paymentMethod, String[] err) {
        Document d = findById(docId);
        if (d == null) { err[0] = "Document introuvable"; return -1; }
        if (!"open".equals(d.status)) { err[0] = "Déjà converti en facture"; return -1; }
        if ("credit".equals(paymentMethod) && d.clientId <= 0) { err[0] = "Un client est requis pour une vente à crédit"; return -1; }
        for (SaleItem i : d.items) {
            Cursor c = db.rawQuery("SELECT stock FROM " + DatabaseHelper.T_PRODUCTS + " WHERE id=?", new String[]{String.valueOf(i.productId)});
            try {
                if (!c.moveToFirst()) { err[0] = "Produit supprimé : " + i.productName; return -1; }
                if (c.getInt(0) < i.quantity) { err[0] = "Stock insuffisant : " + i.productName + " (" + c.getInt(0) + ")"; return -1; }
            } finally { c.close(); }
        }
        long saleId = sales.createSale(paymentMethod, d.items, d.clientId, d.clientName, d.clientPhone, "");
        if (saleId < 0) { err[0] = "Erreur d'enregistrement"; return -1; }
        ContentValues cv = new ContentValues();
        cv.put("status", "converted"); cv.put("converted_sale_id", saleId);
        db.update(DatabaseHelper.T_DOCUMENTS, cv, "id=?", new String[]{String.valueOf(docId)});
        return saleId;
    }

    private Document fromCursor(Cursor c) {
        Document d = new Document();
        d.id = c.getLong(c.getColumnIndexOrThrow("id"));
        d.docType = c.getString(c.getColumnIndexOrThrow("doc_type"));
        d.number = c.getString(c.getColumnIndexOrThrow("number"));
        d.clientId = c.getLong(c.getColumnIndexOrThrow("client_id"));
        d.clientName = c.getString(c.getColumnIndexOrThrow("client_name"));
        d.clientPhone = c.getString(c.getColumnIndexOrThrow("client_phone"));
        d.subtotal = c.getDouble(c.getColumnIndexOrThrow("subtotal"));
        d.taxRate = c.getDouble(c.getColumnIndexOrThrow("tax_rate"));
        d.taxAmount = c.getDouble(c.getColumnIndexOrThrow("tax_amount"));
        d.totalAmount = c.getDouble(c.getColumnIndexOrThrow("total_amount"));
        d.validUntil = c.getString(c.getColumnIndexOrThrow("valid_until"));
        d.status = c.getString(c.getColumnIndexOrThrow("status"));
        d.convertedSaleId = c.getLong(c.getColumnIndexOrThrow("converted_sale_id"));
        d.createdAt = c.getString(c.getColumnIndexOrThrow("created_at"));
        return d;
    }
}
