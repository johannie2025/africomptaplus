package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.PendingSale;
import com.wisedesign.africomptaplus.models.SaleItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PendingSaleService {
    private final DatabaseHelper db;

    public PendingSaleService(Context context) {
        db = DatabaseHelper.getInstance(context);
    }

    public long save(List<SaleItem> items, long clientId, String clientName, String clientPhone,
                     String paymentMethod, String dueDate, String discountType,
                     double discountValue, boolean vatEnabled, double vatRate) {
        if (items == null || items.isEmpty()) return -1;
        try {
            JSONArray serializedItems = new JSONArray();
            for (SaleItem item : items) {
                JSONObject row = new JSONObject();
                row.put("product_id", item.productId);
                row.put("product_name", item.productName);
                row.put("buying_price", item.buyingPrice);
                row.put("quantity", item.quantity);
                row.put("unit_price", item.unitPrice);
                row.put("total_price", item.totalPrice);
                serializedItems.put(row);
            }
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_CLIENT_ID, clientId);
            values.put(DatabaseHelper.COL_CLIENT_NAME, clientName);
            values.put(DatabaseHelper.COL_CLIENT_PHONE, clientPhone);
            values.put(DatabaseHelper.COL_PAYMENT_METHOD, paymentMethod);
            values.put(DatabaseHelper.COL_DUE_DATE, dueDate);
            values.put(DatabaseHelper.COL_DISCOUNT_TYPE, discountType);
            values.put(DatabaseHelper.COL_DISCOUNT_VALUE, discountValue);
            values.put(DatabaseHelper.COL_VAT_RATE, vatRate);
            values.put("vat_enabled", vatEnabled ? 1 : 0);
            values.put("items_json", serializedItems.toString());
            values.put(DatabaseHelper.COL_CREATED_AT,
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            return db.insert(DatabaseHelper.T_PENDING_SALES, values);
        } catch (JSONException e) {
            return -1;
        }
    }

    public List<PendingSale> findAll() {
        List<PendingSale> drafts = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.T_PENDING_SALES
                + " ORDER BY " + DatabaseHelper.COL_CREATED_AT + " DESC", null);
        try {
            while (cursor.moveToNext()) drafts.add(fromCursor(cursor));
        } finally {
            cursor.close();
        }
        return drafts;
    }

    public boolean delete(long id) {
        return db.delete(DatabaseHelper.T_PENDING_SALES, DatabaseHelper.COL_ID + "=?",
                new String[]{String.valueOf(id)}) > 0;
    }

    private PendingSale fromCursor(Cursor cursor) {
        PendingSale draft = new PendingSale();
        draft.id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
        draft.clientId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CLIENT_ID));
        draft.clientName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CLIENT_NAME));
        draft.clientPhone = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CLIENT_PHONE));
        draft.paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PAYMENT_METHOD));
        draft.dueDate = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DUE_DATE));
        draft.discountType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DISCOUNT_TYPE));
        draft.discountValue = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DISCOUNT_VALUE));
        draft.vatRate = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_VAT_RATE));
        draft.vatEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("vat_enabled")) == 1;
        draft.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT));
        try {
            JSONArray rows = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow("items_json")));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                SaleItem item = new SaleItem(row.getLong("product_id"), row.getString("product_name"),
                        row.getDouble("buying_price"), row.getInt("quantity"), row.getDouble("unit_price"));
                item.totalPrice = row.getDouble("total_price");
                draft.items.add(item);
            }
        } catch (JSONException ignored) {
            draft.items.clear();
        }
        return draft;
    }
}
