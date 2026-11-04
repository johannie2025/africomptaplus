package com.wisedesign.africomptaplus.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "africompta.db";
    private static final int    DB_VERSION = 2; // bumped for new tables

    // ── Tables ──────────────────────────────────────────────────────────────
    public static final String T_PRODUCTS     = "products";
    public static final String T_SALES        = "sales";
    public static final String T_SALE_ITEMS   = "sale_items";
    public static final String T_EXPENSES     = "expenses";
    public static final String T_APP_SECURITY = "app_security";
    public static final String T_CLIENTS      = "clients";
    public static final String T_DEBTS        = "debts";
    public static final String T_SHOP_CONFIG  = "shop_config";

    // ── Colonnes communes ────────────────────────────────────────────────────
    public static final String COL_ID            = "id";
    public static final String COL_NAME          = "name";
    public static final String COL_BARCODE       = "barcode";
    public static final String COL_BUYING_PRICE  = "buying_price";
    public static final String COL_SELLING_PRICE = "selling_price";
    public static final String COL_STOCK         = "stock";
    public static final String COL_MIN_STOCK     = "min_stock";
    public static final String COL_INVOICE_NUMBER  = "invoice_number";
    public static final String COL_TOTAL_AMOUNT    = "total_amount";
    public static final String COL_PAYMENT_METHOD  = "payment_method";
    public static final String COL_CREATED_AT      = "created_at";
    public static final String COL_SALE_ID     = "sale_id";
    public static final String COL_PRODUCT_ID  = "product_id";
    public static final String COL_QUANTITY    = "quantity";
    public static final String COL_UNIT_PRICE  = "unit_price";
    public static final String COL_TOTAL_PRICE = "total_price";
    public static final String COL_CATEGORY    = "category";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_AMOUNT      = "amount";
    public static final String COL_DATE        = "date";
    public static final String COL_FIRST_LAUNCH_DATE = "first_launch_date";
    public static final String COL_LAST_KNOWN_DATE   = "last_known_date";
    public static final String COL_IS_ACTIVATED      = "is_activated";
    public static final String COL_LICENSE_KEY        = "license_key";

    // ── Clients ──────────────────────────────────────────────────────────────
    public static final String COL_PHONE   = "phone";
    public static final String COL_EMAIL   = "email";
    public static final String COL_ADDRESS = "address";
    public static final String COL_NOTES   = "notes";

    // ── Dettes ───────────────────────────────────────────────────────────────
    public static final String COL_CLIENT_ID   = "client_id";
    public static final String COL_DUE_DATE    = "due_date";
    public static final String COL_PAID_AMOUNT = "paid_amount";
    public static final String COL_STATUS      = "status"; // open|partial|paid
    public static final String COL_UPDATED_AT  = "updated_at";

    // ── Sales (client) ───────────────────────────────────────────────────────
    public static final String COL_CLIENT_NAME  = "client_name";
    public static final String COL_CLIENT_PHONE = "client_phone";

    // ── Shop config ──────────────────────────────────────────────────────────
    public static final String COL_KEY   = "cfg_key";
    public static final String COL_VALUE = "cfg_value";

    // ── Singleton ────────────────────────────────────────────────────────────
    private static DatabaseHelper sInstance;
    public static synchronized DatabaseHelper getInstance(Context ctx) {
        if (sInstance == null) sInstance = new DatabaseHelper(ctx.getApplicationContext());
        return sInstance;
    }
    private DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    // ── Schema ───────────────────────────────────────────────────────────────
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys = ON;");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_PRODUCTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_NAME + " TEXT NOT NULL,"
                + COL_BARCODE + " TEXT,"
                + COL_BUYING_PRICE + " REAL NOT NULL DEFAULT 0,"
                + COL_SELLING_PRICE + " REAL NOT NULL DEFAULT 0,"
                + COL_STOCK + " INTEGER NOT NULL DEFAULT 0,"
                + COL_MIN_STOCK + " INTEGER NOT NULL DEFAULT 5,"
                + COL_CATEGORY + " TEXT DEFAULT 'Général'"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_CLIENTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_NAME + " TEXT NOT NULL DEFAULT 'Client Passager',"
                + COL_PHONE + " TEXT,"
                + COL_EMAIL + " TEXT,"
                + COL_ADDRESS + " TEXT,"
                + COL_NOTES + " TEXT,"
                + COL_CREATED_AT + " TEXT NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SALES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_INVOICE_NUMBER + " TEXT UNIQUE NOT NULL,"
                + COL_CLIENT_ID + " INTEGER DEFAULT 0,"
                + COL_CLIENT_NAME + " TEXT DEFAULT 'Client Passager',"
                + COL_CLIENT_PHONE + " TEXT DEFAULT '',"
                + COL_TOTAL_AMOUNT + " REAL NOT NULL DEFAULT 0,"
                + COL_PAYMENT_METHOD + " TEXT NOT NULL DEFAULT 'cash',"
                + COL_CREATED_AT + " TEXT NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SALE_ITEMS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_SALE_ID + " INTEGER NOT NULL,"
                + COL_PRODUCT_ID + " INTEGER NOT NULL,"
                + COL_QUANTITY + " INTEGER NOT NULL DEFAULT 1,"
                + COL_UNIT_PRICE + " REAL NOT NULL,"
                + COL_TOTAL_PRICE + " REAL NOT NULL,"
                + "FOREIGN KEY(" + COL_SALE_ID + ") REFERENCES " + T_SALES + "(" + COL_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY(" + COL_PRODUCT_ID + ") REFERENCES " + T_PRODUCTS + "(" + COL_ID + ")"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_EXPENSES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_CATEGORY + " TEXT NOT NULL,"
                + COL_DESCRIPTION + " TEXT,"
                + COL_AMOUNT + " REAL NOT NULL DEFAULT 0,"
                + COL_DATE + " TEXT NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DEBTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_CLIENT_ID + " INTEGER NOT NULL,"
                + COL_SALE_ID + " INTEGER DEFAULT 0,"
                + COL_AMOUNT + " REAL NOT NULL DEFAULT 0,"
                + COL_PAID_AMOUNT + " REAL NOT NULL DEFAULT 0,"
                + COL_DUE_DATE + " TEXT,"
                + COL_STATUS + " TEXT NOT NULL DEFAULT 'open',"
                + COL_NOTES + " TEXT,"
                + COL_CREATED_AT + " TEXT NOT NULL,"
                + COL_UPDATED_AT + " TEXT NOT NULL,"
                + "FOREIGN KEY(" + COL_CLIENT_ID + ") REFERENCES " + T_CLIENTS + "(" + COL_ID + ")"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_APP_SECURITY + " ("
                + COL_FIRST_LAUNCH_DATE + " INTEGER NOT NULL,"
                + COL_LAST_KNOWN_DATE + " INTEGER NOT NULL,"
                + COL_IS_ACTIVATED + " INTEGER NOT NULL DEFAULT 0,"
                + COL_LICENSE_KEY + " TEXT DEFAULT ''"
                + ");");

        // Clé/valeur config boutique
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SHOP_CONFIG + " ("
                + COL_KEY + " TEXT PRIMARY KEY,"
                + COL_VALUE + " TEXT NOT NULL DEFAULT ''"
                + ");");

        // Valeurs par défaut config boutique
        insertConfig(db, "shop_name",    "Ma Boutique");
        insertConfig(db, "shop_phone",   "");
        insertConfig(db, "shop_address", "");
        insertConfig(db, "shop_logo_path", "");
        insertConfig(db, "shop_currency", "XAF");
        insertConfig(db, "invoice_footer", "Merci pour votre achat !");
        insertConfig(db, "cloud_backup_enabled", "0");
        insertConfig(db, "cloud_backup_token", "");

        // Client passager par défaut
        db.execSQL("INSERT OR IGNORE INTO " + T_CLIENTS
                + " (" + COL_ID + "," + COL_NAME + "," + COL_CREATED_AT + ")"
                + " VALUES (0,'Client Passager', datetime('now'))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Migration v1 → v2 : ajout colonnes sans recréer
            try { db.execSQL("ALTER TABLE " + T_SALES + " ADD COLUMN " + COL_CLIENT_ID + " INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_SALES + " ADD COLUMN " + COL_CLIENT_NAME + " TEXT DEFAULT 'Client Passager'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_SALES + " ADD COLUMN " + COL_CLIENT_PHONE + " TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_APP_SECURITY + " ADD COLUMN " + COL_LICENSE_KEY + " TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_PRODUCTS + " ADD COLUMN " + COL_CATEGORY + " TEXT DEFAULT 'Général'"); } catch (Exception ignored) {}
            // Nouvelles tables
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_CLIENTS + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COL_NAME + " TEXT NOT NULL DEFAULT 'Client Passager',"
                    + COL_PHONE + " TEXT," + COL_EMAIL + " TEXT,"
                    + COL_ADDRESS + " TEXT," + COL_NOTES + " TEXT,"
                    + COL_CREATED_AT + " TEXT NOT NULL);");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DEBTS + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COL_CLIENT_ID + " INTEGER NOT NULL,"
                    + COL_SALE_ID + " INTEGER DEFAULT 0,"
                    + COL_AMOUNT + " REAL NOT NULL DEFAULT 0,"
                    + COL_PAID_AMOUNT + " REAL NOT NULL DEFAULT 0,"
                    + COL_DUE_DATE + " TEXT," + COL_STATUS + " TEXT NOT NULL DEFAULT 'open',"
                    + COL_NOTES + " TEXT,"
                    + COL_CREATED_AT + " TEXT NOT NULL," + COL_UPDATED_AT + " TEXT NOT NULL,"
                    + "FOREIGN KEY(" + COL_CLIENT_ID + ") REFERENCES " + T_CLIENTS + "(" + COL_ID + "));");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SHOP_CONFIG + " ("
                    + COL_KEY + " TEXT PRIMARY KEY," + COL_VALUE + " TEXT NOT NULL DEFAULT '');");
            insertConfig(db, "shop_name", "Ma Boutique");
            insertConfig(db, "shop_phone", "");
            insertConfig(db, "shop_address", "");
            insertConfig(db, "shop_logo_path", "");
            insertConfig(db, "shop_currency", "XAF");
            insertConfig(db, "invoice_footer", "Merci pour votre achat !");
            insertConfig(db, "cloud_backup_enabled", "0");
            insertConfig(db, "cloud_backup_token", "");
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) db.execSQL("PRAGMA foreign_keys = ON;");
    }

    // ── Config helpers ───────────────────────────────────────────────────────
    private void insertConfig(SQLiteDatabase db, String key, String value) {
        db.execSQL("INSERT OR IGNORE INTO " + T_SHOP_CONFIG + " VALUES (?,?)", new Object[]{key, value});
    }

    public String getConfig(String key) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_VALUE + " FROM " + T_SHOP_CONFIG + " WHERE " + COL_KEY + "=?",
                new String[]{key});
        try { return c.moveToFirst() ? c.getString(0) : ""; } finally { c.close(); }
    }

    public void setConfig(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put(COL_KEY, key); cv.put(COL_VALUE, value);
        getWritableDatabase().insertWithOnConflict(T_SHOP_CONFIG, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ── CRUD génériques ──────────────────────────────────────────────────────
    public long insert(String table, ContentValues cv) { return getWritableDatabase().insert(table, null, cv); }
    public int  update(String table, ContentValues cv, String where, String[] args) { return getWritableDatabase().update(table, cv, where, args); }
    public int  delete(String table, String where, String[] args) { return getWritableDatabase().delete(table, where, args); }
    public Cursor query(String table, String[] cols, String sel, String[] selArgs, String groupBy, String having, String orderBy) { return getReadableDatabase().query(table, cols, sel, selArgs, groupBy, having, orderBy); }
    public Cursor rawQuery(String sql, String[] args) { return getReadableDatabase().rawQuery(sql, args); }
}
