package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wisedesign.africomptaplus.db.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Synchronisation avec le panel web (hub = serveur).
 * - Chaque ligne locale reçoit un identifiant global (uid) dans la table sync_map : aucune table métier n'est modifiée.
 * - Produits / clients / fournisseurs / dépenses : envoyés quand ils changent (hash) ; le serveur renvoie ce qui a changé côté web.
 * - Ventes, achats, paiements : insérés une seule fois (idempotent par uid), jamais dupliqués.
 * - Stock : échangé par deltas (stock local − stock au dernier sync) pour ne perdre aucune vente faite sur l'autre support.
 */
public class SyncService {
    private static final String TAG = "SyncService";
    public interface Cb { void done(boolean ok, String msg); }

    private static final Object LOCK = new Object();
    private static volatile long lastTry = 0;

    private final Context ctx;
    private final DatabaseHelper db;
    private final SecurityManager sec;

    public SyncService(Context c) { ctx = c.getApplicationContext(); db = DatabaseHelper.getInstance(ctx); sec = new SecurityManager(ctx); }

    // ── État ────────────────────────────────────────────────────────────────
    public boolean enabled() { return "1".equals(db.getConfig("web_sync")) && sec.isActivated(); }
    public void setEnabled(boolean on) { db.setConfig("web_sync", on ? "1" : "0"); }
    public String lastInfo() { String s = db.getConfig("web_sync_last"); return s == null ? "" : s; }

    public void syncIfDue() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastTry < 5 * 60 * 1000L) return;
        lastTry = now;
        syncNow(null);
    }

    public void syncNow(Cb cb) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String msg; boolean ok = false;
            synchronized (LOCK) {
                try { msg = runBlocking(); ok = true; }
                catch (Exception e) { Log.w(TAG, "sync: " + e); msg = e.getMessage() != null && !e.getMessage().isEmpty() ? e.getMessage() : "Connexion impossible. Vérifiez votre réseau."; }
            }
            final String m = msg; final boolean k = ok;
            if (k) db.setConfig("web_sync_last", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
            if (cb != null) main.post(() -> cb.done(k, m));
        }).start();
    }

    // ── HTTP ────────────────────────────────────────────────────────────────
    private JSONObject post(JSONObject b) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(SecurityManager.apiBase() + "?action=sync").openConnection();
        try {
            c.setRequestMethod("POST"); c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(15000); c.setReadTimeout(90000); c.setDoOutput(true);
            b.put("license_key", sec.licenseKey().trim().toUpperCase()); b.put("device_id", sec.deviceId());
            b.put("device_name", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL); b.put("app_version", "3.2");
            try (OutputStream os = c.getOutputStream()) { os.write(b.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(code < 400 ? c.getInputStream() : c.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(); String l; while ((l = br.readLine()) != null) sb.append(l); br.close();
            return new JSONObject(sb.toString());
        } finally { c.disconnect(); }
    }

    // ── Utilitaires ─────────────────────────────────────────────────────────
    private static String md5(String s) {
        try { byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); for (byte x : d) sb.append(String.format("%02x", x)); return sb.toString(); }
        catch (Exception e) { return s; }
    }
    private static String h(Object... f) { StringBuilder sb = new StringBuilder(); for (Object o : f) sb.append(o == null ? "" : String.valueOf(o)).append('\u0001'); return md5(sb.toString()); }
    private static String s(Cursor c, int i) { String v = c.isNull(i) ? "" : c.getString(i); return v == null ? "" : v; }
    private static String toUtc(String local) { return conv(local, TimeZone.getDefault(), TimeZone.getTimeZone("UTC")); }
    private static String fromUtc(String utc) { return conv(utc, TimeZone.getTimeZone("UTC"), TimeZone.getDefault()); }
    private static String conv(String v, TimeZone from, TimeZone to) {
        if (v == null || v.length() < 19) return v == null ? "" : v;   // simple date : inchangée
        try {
            SimpleDateFormat a = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US); a.setTimeZone(from);
            SimpleDateFormat b = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US); b.setTimeZone(to);
            return b.format(a.parse(v.substring(0, 19)));
        } catch (Exception e) { return v; }
    }
    private static String nowLocal() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }

    private SQLiteDatabase d;

    private String q1(String sql, String... a) { Cursor c = d.rawQuery(sql, a); try { return c.moveToFirst() ? s(c, 0) : null; } finally { c.close(); } }
    private String uidOf(String e, long id) {
        String u = q1("SELECT uid FROM sync_map WHERE entity=? AND local_id=?", e, String.valueOf(id));
        if (u != null) return u;
        u = "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        ContentValues cv = new ContentValues(); cv.put("entity", e); cv.put("local_id", id); cv.put("uid", u); cv.put("hash", ""); cv.put("stk", 0);
        d.insertWithOnConflict("sync_map", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        return u;
    }
    private long localId(String e, String uid) {
        if (uid == null || uid.isEmpty()) return -1;
        String v = q1("SELECT local_id FROM sync_map WHERE entity=? AND uid=?", e, uid);
        return v == null ? -1 : Long.parseLong(v);
    }
    private String hashOfMap(String e, long id) { String v = q1("SELECT hash FROM sync_map WHERE entity=? AND local_id=?", e, String.valueOf(id)); return v == null ? "" : v; }
    private int stkOfMap(String e, long id) { String v = q1("SELECT stk FROM sync_map WHERE entity=? AND local_id=?", e, String.valueOf(id)); return v == null ? 0 : Integer.parseInt(v); }
    private void setHash(String e, long id, String hash, Integer stk) {
        uidOf(e, id);
        ContentValues cv = new ContentValues(); cv.put("hash", hash); if (stk != null) cv.put("stk", stk);
        d.update("sync_map", cv, "entity=? AND local_id=?", new String[]{e, String.valueOf(id)});
    }
    private String baseOf(String e, long id) { String v = q1("SELECT base FROM sync_map WHERE entity=? AND local_id=?", e, String.valueOf(id)); return v == null ? "" : v; }
    private void setBase(String e, long id, JSONObject src, String... keys) {
        try {
            JSONObject b = new JSONObject(); for (String k : keys) if (src.has(k)) b.put(k, src.get(k));
            ContentValues cv = new ContentValues(); cv.put("base", b.toString()); d.update("sync_map", cv, "entity=? AND local_id=?", new String[]{e, String.valueOf(id)});
        } catch (Exception ignored) {}
    }
    private JSONObject withBase(JSONObject o, String e, long id) throws Exception {
        String b = baseOf(e, id); if (!b.isEmpty()) try { o.put("base", new JSONObject(b)); } catch (Exception ignored) {}
        return o;
    }
    private void mapUid(String e, long id, String uid) {
        ContentValues cv = new ContentValues(); cv.put("entity", e); cv.put("local_id", id); cv.put("uid", uid); cv.put("hash", ""); cv.put("stk", 0);
        d.insertWithOnConflict("sync_map", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // hash "master" de chaque entité (lu en base)
    private String hClient(long id) { Cursor c = d.rawQuery("SELECT name,phone,email,address FROM clients WHERE id=?", new String[]{String.valueOf(id)}); try { return c.moveToFirst() ? h(s(c, 0), s(c, 1), s(c, 2), s(c, 3)) : ""; } finally { c.close(); } }
    private String hSupplier(long id) { Cursor c = d.rawQuery("SELECT name,phone,email,address,notes FROM suppliers WHERE id=?", new String[]{String.valueOf(id)}); try { return c.moveToFirst() ? h(s(c, 0), s(c, 1), s(c, 2), s(c, 3), s(c, 4)) : ""; } finally { c.close(); } }
    private String hProduct(long id) { Cursor c = d.rawQuery("SELECT name,barcode,category,buying_price,selling_price,min_stock FROM products WHERE id=?", new String[]{String.valueOf(id)}); try { return c.moveToFirst() ? h(s(c, 0), s(c, 1), s(c, 2), c.getDouble(3), c.getDouble(4), c.getInt(5)) : ""; } finally { c.close(); } }
    private String hExpense(long id) { Cursor c = d.rawQuery("SELECT category,description,amount,date FROM expenses WHERE id=?", new String[]{String.valueOf(id)}); try { return c.moveToFirst() ? h(s(c, 0), s(c, 1), c.getDouble(2), s(c, 3)) : ""; } finally { c.close(); } }
    private String hDoc(long id) { Cursor c = d.rawQuery("SELECT status,converted_sale_id,total_amount FROM documents WHERE id=?", new String[]{String.valueOf(id)}); try { return c.moveToFirst() ? h(s(c, 0), c.getLong(1), c.getDouble(2)) : ""; } finally { c.close(); } }
    private String hAcc() { StringBuilder sb = new StringBuilder(); Cursor c = d.rawQuery("SELECT code,label FROM acct_accounts ORDER BY code", null); try { while (c.moveToNext()) sb.append(s(c, 0)).append('|').append(s(c, 1)).append(';'); } finally { c.close(); } return md5(sb.toString()); }
    private String hEntry(long id) { Cursor c = d.rawQuery("SELECT entry_date,journal,label,debit_account,credit_account,amount FROM acct_entries WHERE id=?", new String[]{String.valueOf(id)}); try { return c.moveToFirst() ? h(s(c, 0), s(c, 1), s(c, 2), s(c, 3), s(c, 4), c.getDouble(5)) : ""; } finally { c.close(); } }
    private String hSale(long id) {
        Cursor c = d.rawQuery("SELECT s.total_amount, COALESCE(SUM(p.amount),0), COUNT(p.id) FROM sales s LEFT JOIN debts db ON db.sale_id=s.id LEFT JOIN debt_payments p ON p.debt_id=db.id WHERE s.id=? GROUP BY s.id", new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? h(c.getDouble(0), c.getDouble(1), c.getInt(2)) : ""; } finally { c.close(); }
    }
    private String hPurchase(long id) {
        Cursor c = d.rawQuery("SELECT p.total_amount, COALESCE(SUM(x.amount),0), COUNT(x.id) FROM purchases p LEFT JOIN supplier_payments x ON x.purchase_id=p.id WHERE p.id=? GROUP BY p.id", new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? h(c.getDouble(0), c.getDouble(1), c.getInt(2)) : ""; } finally { c.close(); }
    }

    // ── Cycle complet ───────────────────────────────────────────────────────
    private int lastConflicts = 0, lastMerged = 0;
    private String runBlocking() throws Exception {
        lastConflicts = 0; lastMerged = 0;
        d = db.getWritableDatabase();
        resumeInflight();
        JSONObject req = new JSONObject();
        String tok = db.getConfig("web_sync_token"); req.put("token", tok == null ? "" : tok);
        JSONObject cfg = new JSONObject();
        cfg.put("shop_name", nz(db.getConfig("shop_name"))); cfg.put("currency", nz(db.getConfig("shop_currency")));
        cfg.put("tva_enabled", "1".equals(db.getConfig("tva_enabled")) ? "1" : "0"); cfg.put("tax_rate", nz(db.getConfig("tva_rate"))); cfg.put("tax_label", "TVA");
        req.put("config", cfg);
        String mp = db.getConfig("web_sync_merge"); req.put("merge_stock", mp == null || mp.isEmpty() ? "web" : mp);

        // pending : (entité, id, hash) à mémoriser si le serveur accepte
        List<Object[]> pending = new ArrayList<>();
        Map<Long, Integer> stockSnap = new HashMap<>();

        // clients
        JSONArray jc = new JSONArray(); Set<Long> seenC = new HashSet<>();
        Cursor c = d.rawQuery("SELECT id,name,phone,email,address,archived FROM clients WHERE id>0", null);
        while (c.moveToNext()) {
            long id = c.getLong(0);
            if (c.getInt(5) == 1) { if ("ARCH".equals(hashOfMap("customers", id))) seenC.add(id); continue; }   // archivé localement = supprimé pour la synchro
            seenC.add(id); String hh = h(s(c, 1), s(c, 2), s(c, 3), s(c, 4));
            if (hh.equals(hashOfMap("customers", id))) continue;
            jc.put(withBase(new JSONObject().put("uid", uidOf("customers", id)).put("name", s(c, 1)).put("phone", s(c, 2)).put("email", s(c, 3)).put("address", s(c, 4)), "customers", id));
            pending.add(new Object[]{"customers", id, hh, null});
        }
        c.close(); req.put("customers", jc);

        JSONArray js = new JSONArray(); Set<Long> seenS = new HashSet<>();
        c = d.rawQuery("SELECT id,name,phone,email,address,notes,archived FROM suppliers", null);
        while (c.moveToNext()) {
            long id = c.getLong(0);
            if (c.getInt(6) == 1) { if ("ARCH".equals(hashOfMap("suppliers", id))) seenS.add(id); continue; }
            seenS.add(id); String hh = h(s(c, 1), s(c, 2), s(c, 3), s(c, 4), s(c, 5));
            if (hh.equals(hashOfMap("suppliers", id))) continue;
            js.put(withBase(new JSONObject().put("uid", uidOf("suppliers", id)).put("name", s(c, 1)).put("phone", s(c, 2)).put("email", s(c, 3)).put("address", s(c, 4)).put("notes", s(c, 5)), "suppliers", id));
            pending.add(new Object[]{"suppliers", id, hh, null});
        }
        c.close(); req.put("suppliers", js);

        JSONArray jp = new JSONArray(); Set<Long> seenP = new HashSet<>();
        c = d.rawQuery("SELECT id,name,barcode,category,buying_price,selling_price,min_stock,stock,archived FROM products", null);
        while (c.moveToNext()) {
            long id = c.getLong(0); int stock = c.getInt(7);
            if (c.getInt(8) == 1) { if ("ARCH".equals(hashOfMap("products", id))) seenP.add(id); continue; }
            seenP.add(id); stockSnap.put(id, stock);
            String hh = h(s(c, 1), s(c, 2), s(c, 3), c.getDouble(4), c.getDouble(5), c.getInt(6));
            boolean isNew = hashOfMap("products", id).isEmpty(); int delta = isNew ? 0 : stock - stkOfMap("products", id);
            if (!isNew && delta == 0 && hh.equals(hashOfMap("products", id))) { uidOf("products", id); continue; }
            jp.put(withBase(new JSONObject().put("uid", uidOf("products", id)).put("name", s(c, 1)).put("barcode", s(c, 2)).put("category", s(c, 3)).put("buy", c.getDouble(4)).put("sell", c.getDouble(5))
                    .put("min_stock", c.getInt(6)).put("stock", stock).put("delta", delta), "products", id));
            pending.add(new Object[]{"products", id, hh, null});
        }
        c.close(); req.put("products", jp);

        JSONArray je = new JSONArray(); Set<Long> seenE = new HashSet<>();
        c = d.rawQuery("SELECT id,category,description,amount,date FROM expenses", null);
        while (c.moveToNext()) {
            long id = c.getLong(0); seenE.add(id); String hh = h(s(c, 1), s(c, 2), c.getDouble(3), s(c, 4));
            if (hh.equals(hashOfMap("expenses", id))) continue;
            je.put(withBase(new JSONObject().put("uid", uidOf("expenses", id)).put("category", s(c, 1)).put("description", s(c, 2)).put("amount", c.getDouble(3)).put("created_at", toUtc(s(c, 4))), "expenses", id));
            pending.add(new Object[]{"expenses", id, hh, null});
        }
        c.close(); req.put("expenses", je);

        JSONArray jv = new JSONArray(); Set<Long> seenV = new HashSet<>();
        c = d.rawQuery("SELECT id,invoice_number,client_id,client_name,client_phone,subtotal,tax_amount,total_amount,payment_method,created_at FROM sales", null);
        while (c.moveToNext()) {
            long id = c.getLong(0); seenV.add(id); String hh = hSale(id);
            if (hh.equals(hashOfMap("sales", id))) continue;
            JSONObject o = new JSONObject().put("uid", uidOf("sales", id)).put("reference", s(c, 1)).put("client_uid", c.getLong(2) > 0 ? uidOf("customers", c.getLong(2)) : "")
                    .put("name", s(c, 3)).put("phone", s(c, 4)).put("subtotal", c.getDouble(5)).put("tax", c.getDouble(6)).put("total", c.getDouble(7))
                    .put("payment", s(c, 8)).put("created_at", toUtc(s(c, 9)));
            JSONArray items = new JSONArray();
            Cursor ci = d.rawQuery("SELECT si.product_id, p.name, si.quantity, si.unit_price, p.buying_price FROM sale_items si LEFT JOIN products p ON p.id=si.product_id WHERE si.sale_id=?", new String[]{String.valueOf(id)});
            while (ci.moveToNext()) items.put(new JSONObject().put("product_uid", uidOf("products", ci.getLong(0))).put("name", s(ci, 1)).put("qty", ci.getInt(2)).put("price", ci.getDouble(3)).put("buy", ci.getDouble(4)));
            ci.close(); o.put("items", items);
            JSONArray pays = new JSONArray(); String due = "";
            Cursor cp = d.rawQuery("SELECT p.id,p.amount,p.method,p.created_at,db.due_date FROM debts db LEFT JOIN debt_payments p ON p.debt_id=db.id WHERE db.sale_id=?", new String[]{String.valueOf(id)});
            while (cp.moveToNext()) { due = s(cp, 4); if (!cp.isNull(0)) pays.put(new JSONObject().put("uid", uidOf("debt_payments", cp.getLong(0))).put("amount", cp.getDouble(1)).put("method", s(cp, 2)).put("created_at", toUtc(s(cp, 3)))); }
            cp.close(); o.put("payments", pays).put("due", due);
            jv.put(o); pending.add(new Object[]{"sales", id, hh, null});
        }
        c.close(); req.put("sales", jv);

        JSONArray ju = new JSONArray(); Set<Long> seenU = new HashSet<>();
        c = d.rawQuery("SELECT id,reference,supplier_id,supplier_name,total_amount,initial_paid,payment_method,notes,created_at FROM purchases", null);
        while (c.moveToNext()) {
            long id = c.getLong(0); seenU.add(id); String hh = hPurchase(id);
            if (hh.equals(hashOfMap("purchases", id))) continue;
            JSONObject o = new JSONObject().put("uid", uidOf("purchases", id)).put("reference", s(c, 1)).put("supplier_uid", uidOf("suppliers", c.getLong(2))).put("supplier_name", s(c, 3))
                    .put("total", c.getDouble(4)).put("initial_paid", c.getDouble(5)).put("payment", s(c, 6)).put("notes", s(c, 7)).put("created_at", toUtc(s(c, 8)));
            JSONArray items = new JSONArray();
            Cursor ci = d.rawQuery("SELECT product_id, product_name, quantity, unit_cost FROM purchase_items WHERE purchase_id=?", new String[]{String.valueOf(id)});
            while (ci.moveToNext()) items.put(new JSONObject().put("product_uid", uidOf("products", ci.getLong(0))).put("name", s(ci, 1)).put("qty", ci.getInt(2)).put("cost", ci.getDouble(3)));
            ci.close(); o.put("items", items);
            JSONArray pays = new JSONArray();
            Cursor cp = d.rawQuery("SELECT id,amount,method,created_at FROM supplier_payments WHERE purchase_id=?", new String[]{String.valueOf(id)});
            while (cp.moveToNext()) pays.put(new JSONObject().put("uid", uidOf("supplier_payments", cp.getLong(0))).put("amount", cp.getDouble(1)).put("method", s(cp, 2)).put("created_at", toUtc(s(cp, 3))));
            cp.close(); o.put("payments", pays);
            ju.put(o); pending.add(new Object[]{"purchases", id, hh, null});
        }
        c.close(); req.put("purchases", ju);

        // devis / proforma
        JSONArray jd = new JSONArray(); Set<Long> seenD = new HashSet<>();
        c = d.rawQuery("SELECT id,doc_type,number,client_id,client_name,client_phone,subtotal,tax_amount,total_amount,valid_until,status,converted_sale_id,created_at FROM documents", null);
        while (c.moveToNext()) {
            long id = c.getLong(0); seenD.add(id); String hh = hDoc(id);
            if (hh.equals(hashOfMap("documents", id))) continue;
            JSONObject o = new JSONObject().put("uid", uidOf("documents", id)).put("doc_type", s(c, 1)).put("reference", s(c, 2)).put("client_uid", c.getLong(3) > 0 ? uidOf("customers", c.getLong(3)) : "")
                    .put("name", s(c, 4)).put("phone", s(c, 5)).put("subtotal", c.getDouble(6)).put("tax", c.getDouble(7)).put("total", c.getDouble(8)).put("valid_until", s(c, 9))
                    .put("status", s(c, 10)).put("sale_uid", c.getLong(11) > 0 ? uidOf("sales", c.getLong(11)) : "").put("created_at", toUtc(s(c, 12)));
            JSONArray items = new JSONArray();
            Cursor ci = d.rawQuery("SELECT product_id, product_name, quantity, unit_price FROM document_items WHERE document_id=?", new String[]{String.valueOf(id)});
            while (ci.moveToNext()) items.put(new JSONObject().put("product_uid", uidOf("products", ci.getLong(0))).put("name", s(ci, 1)).put("qty", ci.getInt(2)).put("price", ci.getDouble(3)));
            ci.close(); o.put("items", items);
            jd.put(o); pending.add(new Object[]{"documents", id, hh, null});
        }
        c.close(); req.put("documents", jd);

        // écritures comptables manuelles + comptes personnalisés
        JSONArray jn = new JSONArray(); Set<Long> seenN = new HashSet<>();
        c = d.rawQuery("SELECT id,entry_date,journal,label,debit_account,credit_account,amount FROM acct_entries", null);
        while (c.moveToNext()) {
            long id = c.getLong(0); seenN.add(id); String hh = h(s(c, 1), s(c, 2), s(c, 3), s(c, 4), s(c, 5), c.getDouble(6));
            if (hh.equals(hashOfMap("entries", id))) continue;
            jn.put(withBase(new JSONObject().put("uid", uidOf("entries", id)).put("date", s(c, 1)).put("journal", s(c, 2)).put("label", s(c, 3)).put("debit", s(c, 4)).put("credit", s(c, 5)).put("amount", c.getDouble(6)), "entries", id));
            pending.add(new Object[]{"entries", id, hh, null});
        }
        c.close(); req.put("entries", jn);
        String accHash = hAcc();
        if (!accHash.equals(nz(db.getConfig("web_sync_acc")))) {
            JSONArray ja = new JSONArray(); c = d.rawQuery("SELECT code,label FROM acct_accounts", null);
            while (c.moveToNext()) ja.put(new JSONObject().put("code", s(c, 0)).put("label", s(c, 1)));
            c.close(); req.put("accounts", ja); pending.add(new Object[]{"@acc", 0L, accHash, null});
        }

        // suppressions locales
        JSONArray del = new JSONArray(); List<String[]> drop = new ArrayList<>();
        String[][] ents = {{"products", ""}, {"customers", ""}, {"suppliers", ""}, {"expenses", ""}, {"sales", ""}, {"purchases", ""}, {"documents", ""}, {"entries", ""}};
        Map<String, Set<Long>> seen = new HashMap<>(); seen.put("products", seenP); seen.put("customers", seenC); seen.put("suppliers", seenS); seen.put("expenses", seenE); seen.put("sales", seenV); seen.put("purchases", seenU); seen.put("documents", seenD); seen.put("entries", seenN);
        for (String[] en : ents) {
            c = d.rawQuery("SELECT local_id, uid FROM sync_map WHERE entity=?", new String[]{en[0]});
            while (c.moveToNext()) if (!seen.get(en[0]).contains(c.getLong(0))) { del.put(new JSONObject().put("e", en[0]).put("uid", s(c, 1))); drop.add(new String[]{en[0], String.valueOf(c.getLong(0))}); }
            c.close();
        }
        req.put("deleted", del);

        String rid = "r" + UUID.randomUUID().toString().replace("-", "");
        req.put("req_id", rid);
        // journal de la requête sur disque : si la réponse se perd, on rejoue la MÊME requête (le serveur ne la ré-applique pas)
        JSONObject fl = new JSONObject().put("req", req);
        JSONArray fp = new JSONArray(); for (Object[] p : pending) fp.put(new JSONArray().put(p[0]).put((Long) p[1]).put(p[2]));
        JSONArray fd = new JSONArray(); for (String[] x : drop) fd.put(new JSONArray().put(x[0]).put(x[1]));
        JSONObject fs = new JSONObject(); for (Map.Entry<Long, Integer> en : stockSnap.entrySet()) fs.put(String.valueOf(en.getKey()), en.getValue());
        fl.put("pending", fp).put("drop", fd).put("snap", fs);
        writeInflight(fl.toString());
        JSONObject r = post(req);
        if (!r.optBoolean("ok", false)) throw new Exception(r.optString("message", "Erreur de synchronisation"));
        finish(r, pending, drop, stockSnap);
        clearInflight();
        return "OK;c=" + lastConflicts + ";m=" + lastMerged;
    }

    // ── Reprise d'une requête dont la réponse n'a pas été reçue ─────────────
    private java.io.File inflightFile() { return new java.io.File(ctx.getFilesDir(), "web_sync_inflight.json"); }
    private void writeInflight(String j) throws Exception { try (java.io.FileOutputStream o = new java.io.FileOutputStream(inflightFile())) { o.write(j.getBytes(StandardCharsets.UTF_8)); } }
    private void clearInflight() { inflightFile().delete(); }
    private void resumeInflight() throws Exception {
        java.io.File f = inflightFile(); if (!f.exists()) return;
        byte[] buf = new byte[(int) f.length()]; try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { int n = 0; while (n < buf.length) { int k = in.read(buf, n, buf.length - n); if (k < 0) break; n += k; } }
        JSONObject fl = new JSONObject(new String(buf, StandardCharsets.UTF_8));
        List<Object[]> pending = new ArrayList<>(); JSONArray fp = fl.getJSONArray("pending");
        for (int i = 0; i < fp.length(); i++) { JSONArray x = fp.getJSONArray(i); pending.add(new Object[]{x.getString(0), x.getLong(1), x.getString(2), null}); }
        List<String[]> drop = new ArrayList<>(); JSONArray fd = fl.getJSONArray("drop");
        for (int i = 0; i < fd.length(); i++) { JSONArray x = fd.getJSONArray(i); drop.add(new String[]{x.getString(0), x.getString(1)}); }
        Map<Long, Integer> snap = new HashMap<>(); JSONObject fs = fl.getJSONObject("snap");
        java.util.Iterator<String> it = fs.keys(); while (it.hasNext()) { String k = it.next(); snap.put(Long.parseLong(k), fs.getInt(k)); }
        JSONObject r = post(fl.getJSONObject("req"));
        if (!r.optBoolean("ok", false)) throw new Exception(r.optString("message", "Erreur de synchronisation"));
        finish(r, pending, drop, snap);
        clearInflight();
    }

    private void finish(JSONObject r, List<Object[]> pending, List<String[]> drop, Map<Long, Integer> stockSnap) throws Exception {
        lastConflicts += r.optInt("conflicts", 0); lastMerged += r.optInt("merged", 0);
        d.beginTransaction();
        try {
            JSONArray rm = r.optJSONArray("remap");
            if (rm != null) for (int i = 0; i < rm.length(); i++) { JSONObject m = rm.getJSONObject(i); d.execSQL("UPDATE sync_map SET uid=? WHERE entity=? AND uid=?", new Object[]{m.getString("to"), m.getString("e"), m.getString("from")}); }
            for (Object[] p : pending) { if ("@acc".equals(p[0])) db.setConfig("web_sync_acc", (String) p[2]); else setHashIfExists((String) p[0], (Long) p[1], (String) p[2]); }
            for (String[] x : drop) d.delete("sync_map", "entity=? AND local_id=?", x);

            applyDeleted(r.optJSONArray("deleted"));
            applyCustomers(r.optJSONArray("customers"));
            applySuppliers(r.optJSONArray("suppliers"));
            applyProducts(r.optJSONArray("products"), stockSnap);
            applyExpenses(r.optJSONArray("expenses"));
            applySales(r.optJSONArray("sales"));
            applyPurchases(r.optJSONArray("purchases"));
            applyDocuments(r.optJSONArray("documents"));
            applyEntries(r.optJSONArray("entries"));
            applyAccounts(r.optJSONArray("accounts"));
            db.setConfig("web_sync_token", r.optString("token", ""));
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }
    private void setHashIfExists(String e, long id, String hash) {
        // ne recrée pas une ligne locale supprimée entre-temps (ex. reprise après redémarrage)
        ContentValues cv = new ContentValues(); cv.put("hash", hash);
        d.update("sync_map", cv, "entity=? AND local_id=?", new String[]{e, String.valueOf(id)});
    }
    private static String nz(String v) { return v == null ? "" : v; }

    // ── Application des changements reçus ──────────────────────────────────
    private boolean hasHistory(String e, long id) {
        String[] qs;
        if ("products".equals(e)) qs = new String[]{"SELECT 1 FROM sale_items WHERE product_id=? LIMIT 1", "SELECT 1 FROM purchase_items WHERE product_id=? LIMIT 1", "SELECT 1 FROM document_items WHERE product_id=? LIMIT 1"};
        else if ("customers".equals(e)) qs = new String[]{"SELECT 1 FROM sales WHERE client_id=? LIMIT 1", "SELECT 1 FROM debts WHERE client_id=? LIMIT 1", "SELECT 1 FROM documents WHERE client_id=? LIMIT 1"};
        else qs = new String[]{"SELECT 1 FROM purchases WHERE supplier_id=? LIMIT 1"};
        for (String q : qs) if (q1(q, String.valueOf(id)) != null) return true;
        return false;
    }
    private void applyDeleted(JSONArray a) {
        if (a == null) return;
        String[][] tab = {{"products", "products"}, {"customers", "clients"}, {"suppliers", "suppliers"}, {"expenses", "expenses"}, {"sales", "sales"}, {"purchases", "purchases"}, {"documents", "documents"}, {"entries", "acct_entries"}};
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue;
            for (String[] t : tab) if (t[0].equals(o.optString("e"))) {
                long id = localId(t[0], o.optString("uid")); if (id < 0) break;
                String sid = String.valueOf(id);
                try {
                    // le stock n'est jamais touché ici : le support qui supprime a déjà corrigé le stock, qui arrive par la synchro des produits
                    if ("sales".equals(t[0])) {
                        d.execSQL("DELETE FROM debt_payments WHERE debt_id IN (SELECT id FROM debts WHERE sale_id=?)", new Object[]{id});
                        d.execSQL("DELETE FROM debts WHERE sale_id=?", new Object[]{id}); d.execSQL("DELETE FROM sale_items WHERE sale_id=?", new Object[]{id});
                    } else if ("purchases".equals(t[0])) {
                        d.execSQL("DELETE FROM supplier_payments WHERE purchase_id=?", new Object[]{id}); d.execSQL("DELETE FROM purchase_items WHERE purchase_id=?", new Object[]{id});
                    } else if ("documents".equals(t[0])) d.execSQL("DELETE FROM document_items WHERE document_id=?", new Object[]{id});
                    if (("products".equals(t[0]) || "customers".equals(t[0]) || "suppliers".equals(t[0])) && hasHistory(t[0], id)) {   // historique : on archive au lieu de supprimer
                        d.execSQL("UPDATE " + t[1] + " SET archived=1 WHERE id=?", new Object[]{id}); setHash(t[0], id, "ARCH", null); continue;
                    }
                    d.delete(t[1], "id=?", new String[]{sid}); d.delete("sync_map", "entity=? AND local_id=?", new String[]{t[0], sid});
                } catch (Exception e) { Log.w(TAG, "suppression ignorée " + t[0] + " " + id + " : " + e.getMessage()); }
            }
        }
    }
    private void applyCustomers(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("customers", uid);
            ContentValues cv = new ContentValues(); cv.put("name", o.optString("name")); cv.put("phone", o.optString("phone")); cv.put("email", o.optString("email")); cv.put("address", o.optString("address"));
            if (id < 0) { cv.put("created_at", nowLocal()); id = d.insert("clients", null, cv); if (id < 0) continue; mapUid("customers", id, uid); }
            else d.update("clients", cv, "id=?", new String[]{String.valueOf(id)});
            setHash("customers", id, hClient(id), null); setBase("customers", id, o, "name", "phone", "email", "address");
        }
    }
    private void applySuppliers(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("suppliers", uid);
            boolean arch = o.optInt("archived", 0) == 1;
            ContentValues cv = new ContentValues(); cv.put("name", o.optString("name")); cv.put("phone", o.optString("phone")); cv.put("email", o.optString("email")); cv.put("address", o.optString("address")); cv.put("notes", o.optString("notes"));
            if (arch) cv.put("archived", 1);
            if (id < 0) { cv.put("created_at", nowLocal()); id = d.insert("suppliers", null, cv); if (id < 0) continue; mapUid("suppliers", id, uid); }
            else d.update("suppliers", cv, "id=?", new String[]{String.valueOf(id)});
            if (arch) { setHash("suppliers", id, "ARCH", null); continue; }
            setHash("suppliers", id, hSupplier(id), null); setBase("suppliers", id, o, "name", "phone", "email", "address", "notes");
        }
    }
    private void applyProducts(JSONArray a, Map<Long, Integer> snap) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("products", uid); int srv = o.optInt("stock");
            ContentValues cv = new ContentValues(); cv.put("name", o.optString("name")); cv.put("barcode", o.optString("barcode")); cv.put("category", o.optString("category").isEmpty() ? "Général" : o.optString("category"));
            cv.put("buying_price", o.optDouble("buy", 0)); cv.put("selling_price", o.optDouble("sell", 0)); cv.put("min_stock", o.optInt("min_stock", 5));
            if (id < 0) { cv.put("stock", srv); id = d.insert("products", null, cv); if (id < 0) continue; mapUid("products", id, uid); }
            else {
                int cur = 0; String cs = q1("SELECT stock FROM products WHERE id=?", String.valueOf(id)); if (cs != null) cur = Integer.parseInt(cs);
                Integer sn = snap.get(id); int local = sn == null ? 0 : cur - sn;     // ventes faites pendant la synchro
                cv.put("stock", srv + local); d.update("products", cv, "id=?", new String[]{String.valueOf(id)});
            }
            setHash("products", id, hProduct(id), srv); setBase("products", id, o, "name", "barcode", "category", "buy", "sell", "min_stock");
        }
    }
    private void applyExpenses(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("expenses", uid);
            ContentValues cv = new ContentValues(); cv.put("category", o.optString("category")); cv.put("description", o.optString("description")); cv.put("amount", o.optDouble("amount", 0));
            if (id < 0) { cv.put("date", fromUtc(o.optString("created_at", nowLocal()))); id = d.insert("expenses", null, cv); if (id < 0) continue; mapUid("expenses", id, uid); }
            else d.update("expenses", cv, "id=?", new String[]{String.valueOf(id)});
            setHash("expenses", id, hExpense(id), null); setBase("expenses", id, o, "category", "description", "amount");
        }
    }
    private void applySales(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("sales", uid);
            long debtId;
            if (id < 0) {
                String ref = o.optString("reference");
                if (q1("SELECT 1 FROM sales WHERE invoice_number=?", ref) != null) ref = ref + "-W";
                if (q1("SELECT 1 FROM sales WHERE invoice_number=?", ref) != null) continue;
                long cid = localId("customers", o.optString("client_uid")); if (cid < 0) cid = 0;
                double sub = o.optDouble("subtotal", 0), tax = o.optDouble("tax", 0), tot = o.optDouble("total", 0);
                ContentValues cv = new ContentValues();
                cv.put("invoice_number", ref); cv.put("client_id", cid); cv.put("client_name", o.optString("name").isEmpty() ? "Client Passager" : o.optString("name")); cv.put("client_phone", o.optString("phone"));
                cv.put("total_amount", tot); cv.put("subtotal", sub); cv.put("tax_rate", sub > 0 ? Math.round(tax * 1000.0 / sub) / 10.0 : 0); cv.put("tax_amount", tax);
                cv.put("payment_method", o.optString("payment", "cash")); cv.put("created_at", fromUtc(o.optString("created_at", nowLocal())));
                id = d.insert("sales", null, cv); if (id < 0) continue; mapUid("sales", id, uid);
                JSONArray its = o.optJSONArray("items");
                if (its != null) for (int k = 0; k < its.length(); k++) {
                    JSONObject it = its.optJSONObject(k); long pid = it == null ? -1 : localId("products", it.optString("product_uid")); if (pid < 0) continue;
                    ContentValues ic = new ContentValues(); int q = it.optInt("qty", 1); double pr = it.optDouble("price", 0);
                    ic.put("sale_id", id); ic.put("product_id", pid); ic.put("quantity", q); ic.put("unit_price", pr); ic.put("total_price", q * pr);
                    d.insert("sale_items", null, ic);   // le stock n'est PAS modifié : il arrive déjà tel quel du serveur
                }
                debtId = -1;
                if ("credit".equals(o.optString("payment")) && cid > 0) {
                    ContentValues dc = new ContentValues(); String now = nowLocal();
                    dc.put("client_id", cid); dc.put("sale_id", id); dc.put("amount", tot); dc.put("paid_amount", 0.0); dc.put("due_date", o.optString("due", "").equals("null") ? "" : o.optString("due", ""));
                    dc.put("status", "open"); dc.put("notes", "Vente " + ref); dc.put("created_at", now); dc.put("updated_at", now);
                    debtId = d.insert("debts", null, dc);
                }
            } else {
                String dv = q1("SELECT id FROM debts WHERE sale_id=? LIMIT 1", String.valueOf(id)); debtId = dv == null ? -1 : Long.parseLong(dv);
            }
            JSONArray pays = o.optJSONArray("payments");
            if (debtId > 0 && pays != null) {
                for (int k = 0; k < pays.length(); k++) {
                    JSONObject p = pays.optJSONObject(k); if (p == null) continue; String pu = p.optString("uid");
                    if (localId("debt_payments", pu) >= 0) continue;
                    ContentValues pc = new ContentValues(); pc.put("debt_id", debtId); pc.put("amount", p.optDouble("amount", 0)); pc.put("method", p.optString("method", "cash")); pc.put("note", ""); pc.put("created_at", fromUtc(p.optString("created_at", nowLocal())));
                    long pid = d.insert("debt_payments", null, pc); if (pid > 0) mapUid("debt_payments", pid, pu);
                }
                String sum = q1("SELECT COALESCE(SUM(amount),0) FROM debt_payments WHERE debt_id=?", String.valueOf(debtId)); double paid = sum == null ? 0 : Double.parseDouble(sum);
                String am = q1("SELECT amount FROM debts WHERE id=?", String.valueOf(debtId)); double amount = am == null ? 0 : Double.parseDouble(am);
                ContentValues up = new ContentValues(); up.put("paid_amount", Math.min(paid, amount)); up.put("status", paid >= amount - 0.001 ? "paid" : (paid > 0 ? "partial" : "open")); up.put("updated_at", nowLocal());
                d.update("debts", up, "id=?", new String[]{String.valueOf(debtId)});
            }
            setHash("sales", id, hSale(id), null);
        }
    }
    private void applyPurchases(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("purchases", uid);
            long sid = localId("suppliers", o.optString("supplier_uid"));
            if (sid < 0 && id < 0 && !o.optString("supplier_uid").isEmpty()) {     // fournisseur supprimé/archivé côté web : fiche archivée pour garder l'achat
                ContentValues sc = new ContentValues(); sc.put("name", o.optString("supplier_name")); sc.put("created_at", nowLocal()); sc.put("archived", 1);
                sid = d.insert("suppliers", null, sc); if (sid > 0) { mapUid("suppliers", sid, o.optString("supplier_uid")); setHash("suppliers", sid, "ARCH", null); }
            }
            double initial = o.optDouble("initial_paid", 0);
            if (id < 0) {
                if (sid < 0) continue;
                String ref = o.optString("reference"); if (q1("SELECT 1 FROM purchases WHERE reference=?", ref) != null) ref = ref + "-W"; if (q1("SELECT 1 FROM purchases WHERE reference=?", ref) != null) continue;
                ContentValues cv = new ContentValues(); cv.put("reference", ref); cv.put("supplier_id", sid); cv.put("supplier_name", o.optString("supplier_name")); cv.put("total_amount", o.optDouble("total", 0));
                cv.put("initial_paid", initial); cv.put("paid_amount", initial); cv.put("payment_method", o.optString("payment", "cash")); cv.put("notes", o.optString("notes")); cv.put("created_at", fromUtc(o.optString("created_at", nowLocal())));
                id = d.insert("purchases", null, cv); if (id < 0) continue; mapUid("purchases", id, uid);
                JSONArray its = o.optJSONArray("items");
                if (its != null) for (int k = 0; k < its.length(); k++) {
                    JSONObject it = its.optJSONObject(k); long pid = it == null ? -1 : localId("products", it.optString("product_uid")); if (pid < 0) continue;
                    ContentValues ic = new ContentValues(); int q = it.optInt("qty", 1); double cs = it.optDouble("cost", 0);
                    ic.put("purchase_id", id); ic.put("product_id", pid); ic.put("product_name", it.optString("name")); ic.put("quantity", q); ic.put("unit_cost", cs); ic.put("total_cost", q * cs);
                    d.insert("purchase_items", null, ic);   // stock déjà reflété par le serveur
                }
            }
            JSONArray pays = o.optJSONArray("payments");
            if (pays != null) for (int k = 0; k < pays.length(); k++) {
                JSONObject p = pays.optJSONObject(k); if (p == null) continue; String pu = p.optString("uid");
                if (localId("supplier_payments", pu) >= 0) continue;
                String ss = q1("SELECT supplier_id FROM purchases WHERE id=?", String.valueOf(id));
                ContentValues pc = new ContentValues(); pc.put("purchase_id", id); pc.put("supplier_id", ss == null ? 0 : Long.parseLong(ss)); pc.put("amount", p.optDouble("amount", 0)); pc.put("method", p.optString("method", "cash")); pc.put("note", ""); pc.put("created_at", fromUtc(p.optString("created_at", nowLocal())));
                long pid = d.insert("supplier_payments", null, pc); if (pid > 0) mapUid("supplier_payments", pid, pu);
            }
            String sum = q1("SELECT COALESCE(SUM(amount),0) FROM supplier_payments WHERE purchase_id=?", String.valueOf(id)); String tt = q1("SELECT total_amount FROM purchases WHERE id=?", String.valueOf(id)); String ip = q1("SELECT initial_paid FROM purchases WHERE id=?", String.valueOf(id));
            double paid = Math.min(tt == null ? 0 : Double.parseDouble(tt), (ip == null ? 0 : Double.parseDouble(ip)) + (sum == null ? 0 : Double.parseDouble(sum)));
            ContentValues up = new ContentValues(); up.put("paid_amount", paid); d.update("purchases", up, "id=?", new String[]{String.valueOf(id)});
            setHash("purchases", id, hPurchase(id), null);
        }
    }

    private void applyDocuments(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("documents", uid);
            long saleId = localId("sales", o.optString("sale_uid"));
            if (id < 0) {
                String num = o.optString("reference"); if (q1("SELECT 1 FROM documents WHERE number=?", num) != null) num = num + "-W"; if (q1("SELECT 1 FROM documents WHERE number=?", num) != null) continue;
                long cid = localId("customers", o.optString("client_uid")); if (cid < 0) cid = 0;
                double sub = o.optDouble("subtotal", 0), tax = o.optDouble("tax", 0); String vu = o.optString("valid_until", ""); if (vu.equals("null")) vu = "";
                ContentValues cv = new ContentValues(); cv.put("doc_type", o.optString("doc_type", "quote")); cv.put("number", num); cv.put("client_id", cid);
                cv.put("client_name", o.optString("name").isEmpty() ? "Client Passager" : o.optString("name")); cv.put("client_phone", o.optString("phone"));
                cv.put("subtotal", sub); cv.put("tax_rate", sub > 0 ? Math.round(tax * 1000.0 / sub) / 10.0 : 0); cv.put("tax_amount", tax); cv.put("total_amount", o.optDouble("total", 0));
                cv.put("valid_until", vu); cv.put("status", o.optString("status", "open")); cv.put("converted_sale_id", saleId > 0 ? saleId : 0); cv.put("created_at", fromUtc(o.optString("created_at", nowLocal())));
                id = d.insert("documents", null, cv); if (id < 0) continue; mapUid("documents", id, uid);
                JSONArray its = o.optJSONArray("items");
                if (its != null) for (int k = 0; k < its.length(); k++) {
                    JSONObject it = its.optJSONObject(k); long pid = it == null ? -1 : localId("products", it.optString("product_uid")); if (pid < 0) continue;
                    ContentValues ic = new ContentValues(); int q = it.optInt("qty", 1); double pr = it.optDouble("price", 0);
                    ic.put("document_id", id); ic.put("product_id", pid); ic.put("product_name", it.optString("name")); ic.put("quantity", q); ic.put("unit_price", pr); ic.put("total_price", q * pr);
                    d.insert("document_items", null, ic);
                }
            } else {
                ContentValues cv = new ContentValues(); cv.put("status", o.optString("status", "open")); if (saleId > 0) cv.put("converted_sale_id", saleId);
                d.update("documents", cv, "id=?", new String[]{String.valueOf(id)});
            }
            setHash("documents", id, hDoc(id), null);
        }
    }
    private void applyEntries(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue; String uid = o.optString("uid"); long id = localId("entries", uid);
            ContentValues cv = new ContentValues(); cv.put("entry_date", o.optString("date")); cv.put("journal", o.optString("journal", "OD")); cv.put("label", o.optString("label"));
            cv.put("debit_account", o.optString("debit")); cv.put("credit_account", o.optString("credit")); cv.put("amount", o.optDouble("amount", 0));
            if (id < 0) { cv.put("created_at", nowLocal()); id = d.insert("acct_entries", null, cv); if (id < 0) continue; mapUid("entries", id, uid); }
            else d.update("acct_entries", cv, "id=?", new String[]{String.valueOf(id)});
            setHash("entries", id, hEntry(id), null); setBase("entries", id, o, "date", "journal", "label", "debit", "credit", "amount");
        }
    }
    private void applyAccounts(JSONArray a) {
        if (a == null || a.length() == 0) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue;
            ContentValues cv = new ContentValues(); cv.put("code", o.optString("code")); cv.put("label", o.optString("label"));
            d.insertWithOnConflict("acct_accounts", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
        db.setConfig("web_sync_acc", hAcc());
    }
}
