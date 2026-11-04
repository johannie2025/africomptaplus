package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductService {

    private final DatabaseHelper db;

    public ProductService(Context context) {
        this.db = DatabaseHelper.getInstance(context);
    }

    public long save(Product p) {
        ContentValues cv = toContentValues(p);
        if (p.id == 0) {
            return db.insert(DatabaseHelper.T_PRODUCTS, cv);
        } else {
            db.update(DatabaseHelper.T_PRODUCTS, cv,
                    DatabaseHelper.COL_ID + "=?", new String[]{String.valueOf(p.id)});
            return p.id;
        }
    }

    public boolean delete(long id) {
        return db.delete(DatabaseHelper.T_PRODUCTS,
                DatabaseHelper.COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public Product findById(long id) {
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_PRODUCTS
                + " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1",
                new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally { c.close(); }
    }

    public Product findByBarcode(String barcode) {
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_PRODUCTS
                + " WHERE " + DatabaseHelper.COL_BARCODE + "=? LIMIT 1",
                new String[]{barcode});
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally { c.close(); }
    }

    public List<Product> findAll() {
        List<Product> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_PRODUCTS
                + " ORDER BY " + DatabaseHelper.COL_NAME + " ASC", null);
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally { c.close(); }
        return list;
    }

    public List<Product> search(String query) {
        List<Product> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_PRODUCTS
                + " WHERE " + DatabaseHelper.COL_NAME + " LIKE ?"
                + " OR " + DatabaseHelper.COL_BARCODE + " LIKE ?"
                + " ORDER BY " + DatabaseHelper.COL_NAME + " ASC",
                new String[]{"%" + query + "%", "%" + query + "%"});
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally { c.close(); }
        return list;
    }

    /**
     * CORRECTION : rawQuery() est read-only — utilisation de execSQL() pour le UPDATE.
     * Appelé UNIQUEMENT hors transaction (ex. usage autonome).
     * Dans SaleService, la décrémentation se fait via decrementProductStockInTransaction().
     */
    public void decrementStock(long productId, int qty) {
        db.getWritableDatabase().execSQL(
                "UPDATE " + DatabaseHelper.T_PRODUCTS
                + " SET " + DatabaseHelper.COL_STOCK + " = " + DatabaseHelper.COL_STOCK + " - ?"
                + " WHERE " + DatabaseHelper.COL_ID + " = ?",
                new Object[]{qty, productId});
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private ContentValues toContentValues(Product p) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_NAME,          p.name);
        cv.put(DatabaseHelper.COL_BARCODE,       p.barcode);
        cv.put(DatabaseHelper.COL_BUYING_PRICE,  p.buyingPrice);
        cv.put(DatabaseHelper.COL_SELLING_PRICE, p.sellingPrice);
        cv.put(DatabaseHelper.COL_STOCK,         p.stock);
        cv.put(DatabaseHelper.COL_MIN_STOCK,     p.minStock);
        return cv;
    }

    private Product fromCursor(Cursor c) {
        Product p = new Product();
        p.id           = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        p.name         = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_NAME));
        p.barcode      = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_BARCODE));
        p.buyingPrice  = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_BUYING_PRICE));
        p.sellingPrice = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_SELLING_PRICE));
        p.stock        = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_STOCK));
        p.minStock     = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_MIN_STOCK));
        return p;
    }
}
