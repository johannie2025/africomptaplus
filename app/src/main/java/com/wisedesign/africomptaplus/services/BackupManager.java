package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import androidx.core.content.FileProvider;

import com.wisedesign.africomptaplus.db.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sauvegarde / restauration des données (produits, clients, ventes, dépenses, dettes, identité boutique + logo).
 * Format : JSON compressé (.acbackup). La table de sécurité (licence / essai) n'est JAMAIS exportée ni
 * écrasée : restaurer une ancienne sauvegarde ne peut pas réinitialiser l'essai.
 */
public class BackupManager {
    public static final String EXT = ".acbackup";
    private static final int SCHEMA = 5;
    /** Ordre d'insertion (parents d'abord). */
    private static final String[] TABLES = {
            DatabaseHelper.T_PRODUCTS, DatabaseHelper.T_CLIENTS, DatabaseHelper.T_SALES,
            DatabaseHelper.T_SALE_ITEMS, DatabaseHelper.T_EXPENSES, DatabaseHelper.T_DEBTS,
            DatabaseHelper.T_DEBT_PAYMENTS, DatabaseHelper.T_DOCUMENTS, DatabaseHelper.T_DOC_ITEMS,
            DatabaseHelper.T_ACCT_ENTRIES, DatabaseHelper.T_ACCT_ACCOUNTS,
            DatabaseHelper.T_SHOP_CONFIG};

    private final Context ctx;
    private final DatabaseHelper helper;

    public BackupManager(Context c) {
        ctx = c.getApplicationContext();
        helper = DatabaseHelper.getInstance(ctx);
    }

    // ── Création ────────────────────────────────────────────────────────────
    public byte[] createBackup() throws Exception {
        SQLiteDatabase db = helper.getReadableDatabase();
        JSONObject root = new JSONObject();
        root.put("app", "africompta");
        root.put("schema", SCHEMA);
        root.put("created", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        JSONObject tables = new JSONObject();
        for (String t : TABLES) {
            JSONArray rows = new JSONArray();
            Cursor c = db.rawQuery("SELECT * FROM " + t, null);
            try {
                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        String name = c.getColumnName(i);
                        switch (c.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER: o.put(name, c.getLong(i)); break;
                            case Cursor.FIELD_TYPE_FLOAT:   o.put(name, c.getDouble(i)); break;
                            case Cursor.FIELD_TYPE_NULL:    o.put(name, JSONObject.NULL); break;
                            default:                        o.put(name, c.getString(i));
                        }
                    }
                    if (t.equals(DatabaseHelper.T_SHOP_CONFIG)) {
                        String k = o.optString(DatabaseHelper.COL_KEY);
                        if (k.startsWith("license_") || k.equals("shop_logo_path") || k.startsWith("cloud_")) continue;
                    }
                    rows.put(o);
                }
            } finally { c.close(); }
            tables.put(t, rows);
        }
        root.put("tables", tables);

        String logo = helperLogoPath();
        if (!logo.isEmpty()) {
            try (InputStream in = new FileInputStream(logo)) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                root.put("logo_b64", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private String helperLogoPath() {
        String p = helper.getConfig("shop_logo_path");
        return (p != null && !p.isEmpty() && new File(p).exists()) ? p : "";
    }

    /** Écrit la sauvegarde dans le cache et retourne l'URI de partage (WhatsApp, Drive, email…). */
    public Uri writeBackupFile() throws Exception {
        byte[] data = createBackup();
        File dir = new File(ctx.getExternalCacheDir(), "backups");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, "AfriCompta_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + EXT);
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(data); }
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
    }

    // ── Restauration ────────────────────────────────────────────────────────
    public String describe(byte[] gz) throws Exception {
        JSONObject root = parse(gz);
        JSONObject t = root.getJSONObject("tables");
        return root.optString("created") + " — "
                + t.getJSONArray(DatabaseHelper.T_PRODUCTS).length() + " produit(s), "
                + t.getJSONArray(DatabaseHelper.T_CLIENTS).length() + " client(s), "
                + t.getJSONArray(DatabaseHelper.T_SALES).length() + " vente(s)";
    }

    public void restore(byte[] gz) throws Exception {
        JSONObject root = parse(gz);
        JSONObject tables = root.getJSONObject("tables");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("PRAGMA foreign_keys=OFF");
        db.beginTransaction();
        try {
            for (int i = TABLES.length - 1; i >= 0; i--) {
                String t = TABLES[i];
                if (t.equals(DatabaseHelper.T_SHOP_CONFIG)) {
                    // garde les clés licence/cloud locales
                    db.execSQL("DELETE FROM " + t + " WHERE " + DatabaseHelper.COL_KEY
                            + " NOT LIKE 'license_%' AND " + DatabaseHelper.COL_KEY + " NOT LIKE 'cloud_%'");
                } else db.execSQL("DELETE FROM " + t);
            }
            for (String t : TABLES) {
                JSONArray rows = tables.optJSONArray(t);
                if (rows == null) continue;
                for (int r = 0; r < rows.length(); r++) {
                    JSONObject o = rows.getJSONObject(r);
                    ContentValues cv = new ContentValues();
                    java.util.Iterator<String> it = o.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        Object v = o.get(k);
                        if (v == JSONObject.NULL) cv.putNull(k);
                        else if (v instanceof Integer) cv.put(k, (Integer) v);
                        else if (v instanceof Long) cv.put(k, (Long) v);
                        else if (v instanceof Double) cv.put(k, (Double) v);
                        else cv.put(k, v.toString());
                    }
                    db.insertWithOnConflict(t, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            // client passager toujours présent
            db.execSQL("INSERT OR IGNORE INTO " + DatabaseHelper.T_CLIENTS + " (id,name) VALUES (0,'Client Passager')");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.execSQL("PRAGMA foreign_keys=ON");
        }
        // logo
        String b64 = root.optString("logo_b64", "");
        String path = "";
        if (!b64.isEmpty()) {
            File f = new File(ctx.getFilesDir(), "shop_logo.png");
            try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(Base64.decode(b64, Base64.DEFAULT)); }
            path = f.getAbsolutePath();
        }
        helper.setConfig("shop_logo_path", path);
    }

    private JSONObject parse(byte[] gz) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        }
        JSONObject root = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        if (!"africompta".equals(root.optString("app"))) throw new Exception("Fichier non reconnu");
        if (root.optInt("schema", 0) > SCHEMA) throw new Exception("Sauvegarde créée par une version plus récente de l'app");
        return root;
    }
}
