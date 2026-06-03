package com.wisedesign.africomptaplus.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "africompta.db";
    private static final int    DB_VERSION = 1;

    // ── Tables ──────────────────────────────────────────────────────────────
    public static final String T_PRODUCTS     = "products";
    public static final String T_SALES        = "sales";
    public static final String T_SALE_ITEMS   = "sale_items";
    public static final String T_EXPENSES     = "expenses";
    public static final String T_APP_SECURITY = "app_security";

    // ── Columns ─────────────────────────────────────────────────────────────
    // products
    public static final String COL_ID            = "id";
    public static final String COL_NAME          = "name";
    public static final String COL_BARCODE       = "barcode";
    public static final String COL_BUYING_PRICE  = "buying_price";
    public static final String COL_SELLING_PRICE = "selling_price";
    public static final String COL_STOCK         = "stock";
    public static final String COL_MIN_STOCK     = "min_stock";
    // sales
    public static final String COL_INVOICE_NUMBER  = "invoice_number";
    public static final String COL_TOTAL_AMOUNT    = "total_amount";
    public static final String COL_PAYMENT_METHOD  = "payment_method";
    public static final String COL_CREATED_AT      = "created_at";
    // sale_items
    public static final String COL_SALE_ID     = "sale_id";
    public static final String COL_PRODUCT_ID  = "product_id";
    public static final String COL_QUANTITY    = "quantity";
    public static final String COL_UNIT_PRICE  = "unit_price";
    public static final String COL_TOTAL_PRICE = "total_price";
    // expenses
    public static final String COL_CATEGORY    = "category";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_AMOUNT      = "amount";
    public static final String COL_DATE        = "date";
    // app_security
    public static final String COL_FIRST_LAUNCH_DATE = "first_launch_date";
    public static final String COL_LAST_KNOWN_DATE   = "last_known_date";
    public static final String COL_IS_ACTIVATED      = "is_activated";

    // ── Singleton ────────────────────────────────────────────────────────────
    private static DatabaseHelper sInstance;

    public static synchronized DatabaseHelper getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new DatabaseHelper(ctx.getApplicationContext());
        }
        return sInstance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ── Schema ───────────────────────────────────────────────────────────────
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys = ON;");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_PRODUCTS + " ("
                + COL_ID            + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME          + " TEXT NOT NULL, "
                + COL_BARCODE       + " TEXT, "
                + COL_BUYING_PRICE  + " REAL NOT NULL DEFAULT 0, "
                + COL_SELLING_PRICE + " REAL NOT NULL DEFAULT 0, "
                + COL_STOCK         + " INTEGER NOT NULL DEFAULT 0, "
                + COL_MIN_STOCK     + " INTEGER NOT NULL DEFAULT 5"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SALES + " ("
                + COL_ID             + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_INVOICE_NUMBER + " TEXT UNIQUE NOT NULL, "
                + COL_TOTAL_AMOUNT   + " REAL NOT NULL DEFAULT 0, "
                + COL_PAYMENT_METHOD + " TEXT NOT NULL DEFAULT 'cash', "
                + COL_CREATED_AT     + " TEXT NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SALE_ITEMS + " ("
                + COL_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SALE_ID     + " INTEGER NOT NULL, "
                + COL_PRODUCT_ID  + " INTEGER NOT NULL, "
                + COL_QUANTITY    + " INTEGER NOT NULL DEFAULT 1, "
                + COL_UNIT_PRICE  + " REAL NOT NULL, "
                + COL_TOTAL_PRICE + " REAL NOT NULL, "
                + "FOREIGN KEY(" + COL_SALE_ID    + ") REFERENCES " + T_SALES    + "(" + COL_ID + ") ON DELETE CASCADE, "
                + "FOREIGN KEY(" + COL_PRODUCT_ID + ") REFERENCES " + T_PRODUCTS + "(" + COL_ID + ")"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_EXPENSES + " ("
                + COL_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CATEGORY    + " TEXT NOT NULL, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_AMOUNT      + " REAL NOT NULL DEFAULT 0, "
                + COL_DATE        + " TEXT NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_APP_SECURITY + " ("
                + COL_FIRST_LAUNCH_DATE + " INTEGER NOT NULL, "
                + COL_LAST_KNOWN_DATE   + " INTEGER NOT NULL, "
                + COL_IS_ACTIVATED      + " INTEGER NOT NULL DEFAULT 0"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_SALE_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + T_SALES);
        db.execSQL("DROP TABLE IF EXISTS " + T_EXPENSES);
        db.execSQL("DROP TABLE IF EXISTS " + T_PRODUCTS);
        db.execSQL("DROP TABLE IF EXISTS " + T_APP_SECURITY);
        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }

    // ── Helpers généraux ─────────────────────────────────────────────────────

    /**
     * Insère une ligne et retourne son rowId (-1 si échec).
     */
    public long insert(String table, ContentValues cv) {
        SQLiteDatabase db = getWritableDatabase();
        return db.insert(table, null, cv);
    }

    /**
     * Met à jour des lignes et retourne le nombre de lignes affectées.
     */
    public int update(String table, ContentValues cv, String where, String[] args) {
        SQLiteDatabase db = getWritableDatabase();
        return db.update(table, cv, where, args);
    }

    /**
     * Supprime des lignes et retourne le nombre de lignes affectées.
     */
    public int delete(String table, String where, String[] args) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(table, where, args);
    }

    /**
     * Requête SELECT générale — le Cursor DOIT être fermé par l'appelant.
     */
    public Cursor query(String table, String[] cols, String sel, String[] selArgs,
                        String groupBy, String having, String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(table, cols, sel, selArgs, groupBy, having, orderBy);
    }

    /**
     * Exécute une requête SQL brute et retourne un Cursor — le Cursor DOIT être fermé par l'appelant.
     */
    public Cursor rawQuery(String sql, String[] args) {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery(sql, args);
    }
}
