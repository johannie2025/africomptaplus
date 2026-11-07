package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Client;
import com.wisedesign.africomptaplus.models.SaleItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bot de vente WhatsApp : le serveur est le cerveau (IA + Evolution API). L'app :
 *  - affiche le QR pour lier le numéro du commerçant,
 *  - pousse le catalogue (prix, stock) et la config au serveur,
 *  - importe les ventes conclues par le bot (stock, clients, dettes, comptabilité).
 * La clé Evolution reste côté serveur.
 */
public class BotService {
    private static final String TAG = "BotService";
    public interface Cb { void done(boolean ok, String msg, JSONObject data); }

    private final Context ctx;
    private final DatabaseHelper db;
    private final SecurityManager sec;

    public BotService(Context c) {
        ctx = c.getApplicationContext();
        db = DatabaseHelper.getInstance(ctx);
        sec = new SecurityManager(ctx);
    }

    // ── Config reçue à l'activation de la licence ───────────────────────────
    public JSONObject config() {
        try { String s = db.getConfig("license_bot"); return s == null || s.isEmpty() ? new JSONObject() : new JSONObject(s); }
        catch (Exception e) { return new JSONObject(); }
    }
    public boolean enabled() { return sec.isActivated() && config().optBoolean("enabled", false); }
    public boolean linked() { return "open".equals(config().optString("wa_state")); }

    private String cfg(String k, String d) { String v = db.getConfig(k); return v == null || v.isEmpty() ? d : v; }
    public String momo()        { return cfg("bot_momo", config().optString("momo_info", "")); }
    public String extra()       { return cfg("bot_extra", config().optString("prompt_extra", "")); }
    public boolean allowCredit(){ return "1".equals(cfg("bot_credit", config().optBoolean("allow_credit") ? "1" : "0")); }
    public boolean auto()       { return "1".equals(cfg("bot_auto", config().optBoolean("bot_auto", true) ? "1" : "0")); }
    public void saveSettings(String momo, String extra, boolean credit, boolean auto) {
        db.setConfig("bot_momo", momo); db.setConfig("bot_extra", extra);
        db.setConfig("bot_credit", credit ? "1" : "0"); db.setConfig("bot_auto", auto ? "1" : "0");
    }

    // ── HTTP ────────────────────────────────────────────────────────────────
    private JSONObject post(String action, JSONObject extra) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(SecurityManager.apiBase() + "?action=" + action).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(10000); c.setReadTimeout(30000); c.setDoOutput(true);
            JSONObject b = extra != null ? extra : new JSONObject();
            b.put("license_key", sec.licenseKey().trim().toUpperCase());
            b.put("device_id", sec.deviceId());
            b.put("device_name", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            b.put("app_version", "2.5");
            try (OutputStream os = c.getOutputStream()) { os.write(b.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(code < 400 ? c.getInputStream() : c.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();
            return new JSONObject(sb.toString());
        } finally { c.disconnect(); }
    }

    private void async(Cb cb, Task t) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                JSONObject r = t.run();
                boolean ok = r.optBoolean("ok", false);
                String msg = r.optString("message", "");
                JSONObject bot = r.optJSONObject("bot");
                if (bot != null) db.setConfig("license_bot", bot.toString());
                if (cb != null) main.post(() -> cb.done(ok, msg, r));
            } catch (Exception e) {
                Log.w(TAG, "bot call: " + e.getMessage());
                if (cb != null) main.post(() -> cb.done(false, "Connexion impossible. Vérifiez votre réseau.", null));
            }
        }).start();
    }
    private interface Task { JSONObject run() throws Exception; }

    // ── WhatsApp : QR / statut / déconnexion ────────────────────────────────
    public void connect(Cb cb) { async(cb, () -> post("wa_connect", null)); }
    public void status(Cb cb)  { async(cb, () -> post("wa_status", null)); }
    public void logout(Cb cb)  { async(cb, () -> post("wa_logout", null)); }

    // ── Synchronisation ─────────────────────────────────────────────────────
    private JSONObject configJson() throws Exception {
        ShopCfg s = new ShopCfg();
        JSONObject c = new JSONObject();
        c.put("shop_name", s.name); c.put("currency", s.currency);
        c.put("tax_rate", new SaleService(ctx).taxRate()); c.put("tax_label", s.taxLabel);
        c.put("momo_info", momo()); c.put("prompt_extra", extra());
        c.put("allow_credit", allowCredit()); c.put("bot_auto", auto());
        return c;
    }
    private class ShopCfg {
        String name = cfg("shop_name", ""), currency = cfg("shop_currency", "XAF"), taxLabel = cfg("shop_tax_label", "TVA");
    }

    private JSONArray productsJson() throws Exception {
        JSONArray a = new JSONArray();
        Cursor c = db.rawQuery("SELECT id, name, barcode, selling_price, stock, category FROM " + DatabaseHelper.T_PRODUCTS, null);
        try {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0)); o.put("name", c.getString(1));
                o.put("barcode", c.isNull(2) ? "" : c.getString(2));
                o.put("price", c.getDouble(3)); o.put("stock", c.getInt(4));
                o.put("category", c.isNull(5) ? "" : c.getString(5));
                a.put(o);
            }
        } finally { c.close(); }
        return a;
    }

    /** Importe les ventes du bot puis pousse le catalogue à jour. Bloquant : appeler hors thread UI. */
    public String syncBlocking() throws Exception {
        if (!enabled()) return "";
        int imported = pullOrders();
        JSONObject b = new JSONObject();
        b.put("full", true); b.put("products", productsJson()); b.put("config", configJson());
        JSONObject r = post("bot_sync", b);
        JSONObject bot = r.optJSONObject("bot");
        if (bot != null) db.setConfig("license_bot", bot.toString());
        if (!r.optBoolean("ok", false)) throw new Exception(r.optString("message", "Synchronisation refusée"));
        db.setConfig("bot_last_sync", String.valueOf(System.currentTimeMillis()));
        return imported + " vente(s) WhatsApp importée(s), " + r.optInt("synced") + " produit(s) envoyé(s).";
    }

    public void syncNow(Cb cb) { async(cb, () -> { String m = syncBlocking(); return new JSONObject().put("ok", true).put("message", m); }); }

    /** Synchro silencieuse, au plus une fois par minute (ouverture de l'app, après une vente). */
    public void syncIfDue() {
        if (!sec.isActivated()) return;
        long chk = 0; try { chk = Long.parseLong(cfg("bot_cfg_check", "0")); } catch (Exception ignored) {}
        if (System.currentTimeMillis() - chk > 3_600_000L) {   // relit la config bot (activée par l'admin ?) au plus 1×/h
            db.setConfig("bot_cfg_check", String.valueOf(System.currentTimeMillis()));
            new Thread(() -> { try { JSONObject r = post("status", null); JSONObject bt = r.optJSONObject("bot");
                if (bt != null) db.setConfig("license_bot", bt.toString()); } catch (Exception e) { Log.w(TAG, "cfg: " + e.getMessage()); } }).start();
        }
        if (!enabled()) return;
        long last = 0; try { last = Long.parseLong(cfg("bot_last_sync", "0")); } catch (Exception ignored) {}
        if (System.currentTimeMillis() - last < 60_000) return;
        db.setConfig("bot_last_sync", String.valueOf(System.currentTimeMillis()));
        new Thread(() -> { try { syncBlocking(); } catch (Exception e) { Log.w(TAG, "sync: " + e.getMessage()); } }).start();
    }

    private boolean saleExists(String ref) {
        Cursor c = db.rawQuery("SELECT 1 FROM " + DatabaseHelper.T_SALES + " WHERE " + DatabaseHelper.COL_INVOICE_NUMBER + "=?", new String[]{ref});
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    private int pullOrders() throws Exception {
        JSONObject r = post("bot_orders", null);
        if (!r.optBoolean("ok", false)) throw new Exception(r.optString("message", "Récupération impossible"));
        JSONArray orders = r.optJSONArray("orders");
        if (orders == null || orders.length() == 0) return 0;
        SaleService sales = new SaleService(ctx);
        ClientService clients = new ClientService(ctx);
        ProductService products = new ProductService(ctx);
        JSONArray ack = new JSONArray(); int n = 0;
        for (int i = 0; i < orders.length(); i++) {
            JSONObject o = orders.getJSONObject(i);
            String ref = o.getString("reference");
            if (!saleExists(ref)) {
                List<SaleItem> items = new ArrayList<>();
                JSONArray its = o.getJSONArray("items");
                for (int k = 0; k < its.length(); k++) {
                    JSONObject it = its.getJSONObject(k);
                    double buy = 0; String name = it.optString("name");
                    com.wisedesign.africomptaplus.models.Product p = products.findById(it.optLong("id"));
                    if (p != null) { buy = p.buyingPrice; name = p.name; }
                    items.add(new SaleItem(it.optLong("id"), name, buy, it.optInt("qty"), it.optDouble("price")));
                }
                String phone = "+" + o.optString("phone");
                String name = o.optString("customer_name", "").trim();
                long clientId = 0;
                String pay = o.optString("payment", "cash");
                if (!name.isEmpty() || "credit".equals(pay)) {
                    Client found = null;
                    for (Client c : clients.search(o.optString("phone"))) { if (c.phone != null && c.phone.replace("+", "").equals(o.optString("phone"))) { found = c; break; } }
                    if (found == null) { found = new Client(name.isEmpty() ? "Client WhatsApp" : name, phone); found.id = clients.save(found); }
                    clientId = found.id; name = found.name;
                }
                String created = o.optString("created_at", null);
                try { created = utcToLocal(created); } catch (Exception ignored) {}
                long id = sales.createSale(pay, items, clientId, name.isEmpty() ? "Client WhatsApp" : name, phone, "", ref, created);
                if (id < 0) continue;   // échec : on n'accuse pas réception, nouvel essai à la prochaine synchro
                n++;
            }
            ack.put(ref);
        }
        if (ack.length() > 0) post("bot_ack", new JSONObject().put("refs", ack));
        return n;
    }

    private static String utcToLocal(String s) throws Exception {
        java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        return out.format(in.parse(s));
    }
}
