package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.models.SaleItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SaleService {

    private final DatabaseHelper db;
    private final ProductService productService;

    public SaleService(Context context) {
        this.db             = DatabaseHelper.getInstance(context);
        this.productService = new ProductService(context);
    }

    /**
     * Enregistre une vente complète (sale + items) dans une transaction atomique.
     * Décrémente automatiquement le stock de chaque produit.
     *
     * @return l'ID de la vente créée, ou -1 si échec.
     */
    public long createSale(String paymentMethod, List<SaleItem> items) {
        if (items == null || items.isEmpty()) return -1;

        SQLiteDatabase sqLiteDatabase = db.getWritableDatabase();
        sqLiteDatabase.beginTransaction();
        try {
            // Calcul du montant total
            double total = 0;
            for (SaleItem item : items) total += item.totalPrice;

            // Numéro de facture unique : YYYYMMDD-HHMMSS-msms
            String invoiceNumber = "INV-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            String createdAt     = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

            // Insertion de la vente
            ContentValues saleCV = new ContentValues();
            saleCV.put(DatabaseHelper.COL_INVOICE_NUMBER, invoiceNumber);
            saleCV.put(DatabaseHelper.COL_TOTAL_AMOUNT,   total);
            saleCV.put(DatabaseHelper.COL_PAYMENT_METHOD, paymentMethod);
            saleCV.put(DatabaseHelper.COL_CREATED_AT,     createdAt);
            long saleId = sqLiteDatabase.insert(DatabaseHelper.T_SALES, null, saleCV);

            if (saleId == -1) return -1;

            // Insertion des lignes de vente
            for (SaleItem item : items) {
                ContentValues itemCV = new ContentValues();
                itemCV.put(DatabaseHelper.COL_SALE_ID,     saleId);
                itemCV.put(DatabaseHelper.COL_PRODUCT_ID,  item.productId);
                itemCV.put(DatabaseHelper.COL_QUANTITY,    item.quantity);
                itemCV.put(DatabaseHelper.COL_UNIT_PRICE,  item.unitPrice);
                itemCV.put(DatabaseHelper.COL_TOTAL_PRICE, item.totalPrice);
                sqLiteDatabase.insert(DatabaseHelper.T_SALE_ITEMS, null, itemCV);

                // Décrément du stock
                productService.decrementStock(item.productId, item.quantity);
            }

            sqLiteDatabase.setTransactionSuccessful();
            return saleId;

        } finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /** Récupère toutes les ventes (sans leurs items), ordre décroissant. */
    public List<Sale> findAll() {
        List<Sale> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_SALES
                + " ORDER BY " + DatabaseHelper.COL_CREATED_AT + " DESC", null);
        try {
            while (c.moveToNext()) list.add(saleFromCursor(c));
        } finally { c.close(); }
        return list;
    }

    /** Récupère une vente avec tous ses items (pour la facture PDF). */
    public Sale findByIdWithItems(long saleId) {
        Sale sale = null;
        Cursor c = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.T_SALES
                + " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1",
                new String[]{String.valueOf(saleId)});
        try {
            if (c.moveToFirst()) sale = saleFromCursor(c);
        } finally { c.close(); }

        if (sale == null) return null;
        sale.items = findItemsBySaleId(saleId);
        return sale;
    }

    /** Récupère les items d'une vente avec le nom du produit. */
    public List<SaleItem> findItemsBySaleId(long saleId) {
        List<SaleItem> items = new ArrayList<>();
        String sql =
                "SELECT si.*, p." + DatabaseHelper.COL_NAME + " AS product_name"
                + ", p." + DatabaseHelper.COL_BUYING_PRICE + " AS buying_price"
                + " FROM " + DatabaseHelper.T_SALE_ITEMS + " si"
                + " INNER JOIN " + DatabaseHelper.T_PRODUCTS + " p"
                + " ON p." + DatabaseHelper.COL_ID + " = si." + DatabaseHelper.COL_PRODUCT_ID
                + " WHERE si." + DatabaseHelper.COL_SALE_ID + "=?";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(saleId)});
        try {
            while (c.moveToNext()) {
                SaleItem item = new SaleItem();
                item.id          = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                item.saleId      = saleId;
                item.productId   = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_PRODUCT_ID));
                item.productName = c.getString(c.getColumnIndexOrThrow("product_name"));
                item.buyingPrice = c.getDouble(c.getColumnIndexOrThrow("buying_price"));
                item.quantity    = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY));
                item.unitPrice   = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT_PRICE));
                item.totalPrice  = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_PRICE));
                items.add(item);
            }
        } finally { c.close(); }
        return items;
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private Sale saleFromCursor(Cursor c) {
        Sale s = new Sale();
        s.id            = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        s.invoiceNumber = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_NUMBER));
        s.totalAmount   = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_AMOUNT));
        s.paymentMethod = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_PAYMENT_METHOD));
        s.createdAt     = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT));
        return s;
    }
}
