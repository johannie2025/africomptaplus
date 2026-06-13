package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.models.SaleItem;
import java.text.SimpleDateFormat;
import java.util.*;

public class SaleService {
    private final DatabaseHelper db;
    public SaleService(Context ctx) { db = DatabaseHelper.getInstance(ctx); }

    /**
     * Crée une vente avec client associé.
     * Si paymentMethod == "credit", crée automatiquement une dette.
     */
    public long createSale(String paymentMethod, List<SaleItem> items,
                           long clientId, String clientName, String clientPhone,
                           String dueDate) {
        if (items == null || items.isEmpty()) return -1;
        SQLiteDatabase sqdb = db.getWritableDatabase();
        sqdb.beginTransaction();
        try {
            double total = 0;
            for (SaleItem item : items) total += item.totalPrice;

            String inv = "INV-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

            ContentValues cv = new ContentValues();
            cv.put(DatabaseHelper.COL_INVOICE_NUMBER, inv);
            cv.put(DatabaseHelper.COL_CLIENT_ID,      clientId);
            cv.put(DatabaseHelper.COL_CLIENT_NAME,    clientName  != null ? clientName  : "Client Passager");
            cv.put(DatabaseHelper.COL_CLIENT_PHONE,   clientPhone != null ? clientPhone : "");
            cv.put(DatabaseHelper.COL_TOTAL_AMOUNT,   total);
            cv.put(DatabaseHelper.COL_PAYMENT_METHOD, paymentMethod);
            cv.put(DatabaseHelper.COL_CREATED_AT,     now);
            long saleId = sqdb.insert(DatabaseHelper.T_SALES, null, cv);
            if (saleId == -1) return -1;

            for (SaleItem item : items) {
                ContentValues ic = new ContentValues();
                ic.put(DatabaseHelper.COL_SALE_ID,     saleId);
                ic.put(DatabaseHelper.COL_PRODUCT_ID,  item.productId);
                ic.put(DatabaseHelper.COL_QUANTITY,    item.quantity);
                ic.put(DatabaseHelper.COL_UNIT_PRICE,  item.unitPrice);
                ic.put(DatabaseHelper.COL_TOTAL_PRICE, item.totalPrice);
                sqdb.insert(DatabaseHelper.T_SALE_ITEMS, null, ic);
                // Décrément stock dans la même transaction
                sqdb.execSQL("UPDATE " + DatabaseHelper.T_PRODUCTS
                        + " SET " + DatabaseHelper.COL_STOCK + "=" + DatabaseHelper.COL_STOCK + "-?"
                        + " WHERE " + DatabaseHelper.COL_ID + "=?",
                        new Object[]{item.quantity, item.productId});
            }

            // Crédit → dette automatique
            if ("credit".equals(paymentMethod) && clientId > 0) {
                ContentValues dc = new ContentValues();
                dc.put(DatabaseHelper.COL_CLIENT_ID,   clientId);
                dc.put(DatabaseHelper.COL_SALE_ID,     saleId);
                dc.put(DatabaseHelper.COL_AMOUNT,      total);
                dc.put(DatabaseHelper.COL_PAID_AMOUNT, 0.0);
                dc.put(DatabaseHelper.COL_DUE_DATE,    dueDate != null ? dueDate : "");
                dc.put(DatabaseHelper.COL_STATUS,      "open");
                dc.put(DatabaseHelper.COL_NOTES,       "Vente " + inv);
                dc.put(DatabaseHelper.COL_CREATED_AT,  now);
                dc.put(DatabaseHelper.COL_UPDATED_AT,  now);
                sqdb.insert(DatabaseHelper.T_DEBTS, null, dc);
            }

            sqdb.setTransactionSuccessful();
            return saleId;
        } finally { sqdb.endTransaction(); }
    }

    // compat ancienne signature
    public long createSale(String paymentMethod, List<SaleItem> items) {
        return createSale(paymentMethod, items, 0, "Client Passager", "", null);
    }

    public List<Sale> findAll() {
        List<Sale> list = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_SALES
                + " ORDER BY " + DatabaseHelper.COL_CREATED_AT + " DESC", null);
        try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
        return list;
    }

    public Sale findByIdWithItems(long saleId) {
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_SALES
                + " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1", new String[]{String.valueOf(saleId)});
        Sale sale = null;
        try { if (c.moveToFirst()) sale = fromCursor(c); } finally { c.close(); }
        if (sale == null) return null;
        sale.items = findItemsBySaleId(saleId);
        return sale;
    }

    public List<SaleItem> findItemsBySaleId(long saleId) {
        List<SaleItem> items = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT si.*, p." + DatabaseHelper.COL_NAME + " AS product_name"
                + ", p." + DatabaseHelper.COL_BUYING_PRICE + " AS buying_price"
                + " FROM " + DatabaseHelper.T_SALE_ITEMS + " si"
                + " INNER JOIN " + DatabaseHelper.T_PRODUCTS + " p ON p." + DatabaseHelper.COL_ID + "=si." + DatabaseHelper.COL_PRODUCT_ID
                + " WHERE si." + DatabaseHelper.COL_SALE_ID + "=?", new String[]{String.valueOf(saleId)});
        try {
            while (c.moveToNext()) {
                SaleItem i = new SaleItem();
                i.id          = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                i.saleId      = saleId;
                i.productId   = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_PRODUCT_ID));
                i.productName = c.getString(c.getColumnIndexOrThrow("product_name"));
                i.buyingPrice = c.getDouble(c.getColumnIndexOrThrow("buying_price"));
                i.quantity    = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY));
                i.unitPrice   = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT_PRICE));
                i.totalPrice  = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_PRICE));
                items.add(i);
            }
        } finally { c.close(); }
        return items;
    }

    private Sale fromCursor(Cursor c) {
        Sale s = new Sale();
        s.id            = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        s.invoiceNumber = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_NUMBER));
        s.totalAmount   = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_AMOUNT));
        s.paymentMethod = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_PAYMENT_METHOD));
        s.createdAt     = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT));
        int ciIdx = c.getColumnIndex(DatabaseHelper.COL_CLIENT_ID);
        if (ciIdx >= 0) s.clientId = c.getLong(ciIdx);
        int cnIdx = c.getColumnIndex(DatabaseHelper.COL_CLIENT_NAME);
        if (cnIdx >= 0) s.clientName = c.getString(cnIdx);
        int cpIdx = c.getColumnIndex(DatabaseHelper.COL_CLIENT_PHONE);
        if (cpIdx >= 0) s.clientPhone = c.getString(cpIdx);
        return s;
    }
}
