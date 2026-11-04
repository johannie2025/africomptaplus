package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wisedesign.africomptaplus.db.DatabaseHelper;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * SecurityManager V2 :
 *  - Time-bomb 14 jours + anti-cheat horloge (identique v1)
 *  - Vérification de licence en ligne via API https://wiseos.alwaysdata.net/africompta/api/
 *  - Callback asynchrone (NetworkOnMainThread)
 */
public class SecurityManager {

    private static final String TAG              = "SecurityManager";
    private static final long   TRIAL_MS         = 14L * 24 * 60 * 60 * 1000;
    private static final String API_BASE         = "https://wiseos.alwaysdata.net/africompta/api/";
    private static final long   RECHECK_MS       = 24L * 60 * 60 * 1000;

    public enum LockReason { NONE, CLOCK_FRAUD, TRIAL_EXPIRED, ALREADY_LOCKED }

    public interface LicenseCallback { void onResult(boolean valid, String message); }

    private final DatabaseHelper db;
    private final Context appCtx;
    public SecurityManager(Context ctx) { appCtx = ctx.getApplicationContext(); db = DatabaseHelper.getInstance(appCtx); }

    // ── Vérification locale (time-bomb) ──────────────────────────────────────

    public LockReason checkAndUpdate() {
        long now = System.currentTimeMillis();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT " + DatabaseHelper.COL_FIRST_LAUNCH_DATE
                    + "," + DatabaseHelper.COL_LAST_KNOWN_DATE
                    + "," + DatabaseHelper.COL_IS_ACTIVATED
                    + " FROM " + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);

            if (!cursor.moveToFirst()) { insertRow(now); return LockReason.NONE; }

            long firstLaunch = cursor.getLong(0);
            long lastKnown   = cursor.getLong(1);
            int  activated   = cursor.getInt(2);

            if (activated == 1) { updateLastKnown(now); return LockReason.NONE; }
            if (activated == -1) return LockReason.ALREADY_LOCKED;
            if (now < lastKnown) { hardLock(); return LockReason.CLOCK_FRAUD; }
            if (now - firstLaunch > TRIAL_MS) { hardLock(); return LockReason.TRIAL_EXPIRED; }
            updateLastKnown(now);
            return LockReason.NONE;
        } finally { if (cursor != null) cursor.close(); }
    }

    public int getRemainingDays() {
        Cursor c = db.rawQuery("SELECT " + DatabaseHelper.COL_FIRST_LAUNCH_DATE
                + " FROM " + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);
        try {
            if (!c.moveToFirst()) return 14;
            long elapsed = System.currentTimeMillis() - c.getLong(0);
            return Math.max(0, (int) ((TRIAL_MS - elapsed) / (24L * 3600 * 1000)));
        } finally { c.close(); }
    }

    public boolean isActivated() {
        Cursor c = db.rawQuery("SELECT " + DatabaseHelper.COL_IS_ACTIVATED
                + " FROM " + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);
        try { return c.moveToFirst() && c.getInt(0) == 1; } finally { c.close(); }
    }

    // ── Vérification API distante ─────────────────────────────────────────────

    /**
     * Vérifie et active une clé de licence via l'API.
     * Exécuté en thread background ; résultat renvoyé sur le main thread via callback.
     */
    public void verifyLicense(String licenseKey, String deviceId, LicenseCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                JSONObject resp = call("activate", licenseKey, deviceId);
                boolean valid = resp.optBoolean("valid", false);
                String  msg   = resp.optString("message", "Erreur inconnue");
                if (valid) {
                    activateLocal(licenseKey);
                    JSONObject lic = resp.optJSONObject("license");
                    if (lic != null) {
                        db.setConfig("license_plan", lic.optString("plan", ""));
                        db.setConfig("license_expires", lic.isNull("expires_at") ? "" : lic.optString("expires_at", ""));
                        db.setConfig("license_customer", lic.optString("customer_name", ""));
                    }
                    db.setConfig("license_last_check", String.valueOf(System.currentTimeMillis()));
                }
                main.post(() -> callback.onResult(valid, msg));
            } catch (Exception ex) {
                Log.e(TAG, "verifyLicense error", ex);
                main.post(() -> callback.onResult(false, "Connexion impossible. Vérifiez votre réseau."));
            }
        }).start();
    }

    /** Re-validation silencieuse (max 1×/24 h). Hors-ligne = on ignore ; clé révoquée/expirée = verrouillage. */
    public void recheckOnlineIfDue() {
        if (!isActivated()) return;
        String last = db.getConfig("license_last_check");
        long lastMs = 0;
        try { lastMs = Long.parseLong(last); } catch (Exception ignored) {}
        if (System.currentTimeMillis() - lastMs < RECHECK_MS) return;
        final String key = getLicenseKey();
        if (key.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject resp = call("status", key, deviceId());
                String status = resp.optString("status", "");
                if (resp.optBoolean("valid", false)) {
                    db.setConfig("license_last_check", String.valueOf(System.currentTimeMillis()));
                } else if ("revoked".equals(status) || "expired".equals(status) || "invalid".equals(status)) {
                    hardLock();
                }
            } catch (Exception ex) { Log.w(TAG, "recheck offline: " + ex.getMessage()); }
        }).start();
    }

    public String deviceId() {
        String id = android.provider.Settings.Secure.getString(appCtx.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID);
        return id == null || id.length() < 6 ? "device-unknown" : id;
    }

    private String getLicenseKey() {
        Cursor c = db.rawQuery("SELECT " + DatabaseHelper.COL_LICENSE_KEY + " FROM "
                + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);
        try { return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : ""; } finally { c.close(); }
    }

    private JSONObject call(String action, String key, String deviceId) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_BASE + "?action=" + action).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            JSONObject body = new JSONObject();
            body.put("license_key", key.trim().toUpperCase());
            body.put("device_id",   deviceId);
            body.put("device_name", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            body.put("app_version", "2.0");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    code < 400 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return new JSONObject(sb.toString());
        } finally { conn.disconnect(); }
    }

    /** Activation locale (après réponse API positive). */
    public void activateLocal(String key) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_IS_ACTIVATED, 1);
        cv.put(DatabaseHelper.COL_LICENSE_KEY,  key);
        db.update(DatabaseHelper.T_APP_SECURITY, cv, null, null);
    }

    // ── Privés ───────────────────────────────────────────────────────────────
    private void insertRow(long now) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_FIRST_LAUNCH_DATE, now);
        cv.put(DatabaseHelper.COL_LAST_KNOWN_DATE,   now);
        cv.put(DatabaseHelper.COL_IS_ACTIVATED,      0);
        cv.put(DatabaseHelper.COL_LICENSE_KEY,       "");
        db.insert(DatabaseHelper.T_APP_SECURITY, cv);
    }
    private void updateLastKnown(long now) {
        ContentValues cv = new ContentValues(); cv.put(DatabaseHelper.COL_LAST_KNOWN_DATE, now);
        db.update(DatabaseHelper.T_APP_SECURITY, cv, null, null);
    }
    private void hardLock() {
        ContentValues cv = new ContentValues(); cv.put(DatabaseHelper.COL_IS_ACTIVATED, -1);
        db.update(DatabaseHelper.T_APP_SECURITY, cv, null, null);
    }
}
