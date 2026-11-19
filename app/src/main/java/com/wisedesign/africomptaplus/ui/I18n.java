package com.wisedesign.africomptaplus.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Langue de l'application : FR / EN / ES. Par défaut la langue de l'appareil (sinon anglais),
 * modifiable dans Paramètres. Les textes source sont en français ; le dictionnaire (assets/i18n.json)
 * donne EN/ES. Les écrans sont traduits à l'affichage (vues), les messages/dialogues/PDF via t().
 */
public final class I18n {
    public static final String AUTO = "auto";
    private static final String PREFS = "i18n";
    private static String lang = "fr";
    private static Map<String, String> dict = new HashMap<>();
    private static List<String> fragKeys = new ArrayList<>();
    private static java.util.Set<String> values = new java.util.HashSet<>();   // textes déjà traduits (évite la double traduction)   // clés remplaçables à l'intérieur d'un texte
    private static final Map<String, String> cache = new HashMap<>();
    private static boolean loaded = false;

    private I18n() {}

    /** Préférence enregistrée : auto | fr | en | es. */
    public static String pref(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", AUTO); }

    public static void setPref(Context c, String p) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", p).apply();
        init(c);
    }

    /** Langue effective pour une préférence donnée. */
    public static String resolve(String p) {
        if ("fr".equals(p) || "en".equals(p) || "es".equals(p)) return p;
        String d = Locale.getDefault().getLanguage();
        return "fr".equals(d) || "es".equals(d) || "en".equals(d) ? d : "en";
    }

    public static String lang() { return lang; }

    public static synchronized void init(Context c) {
        lang = resolve(pref(c));
        cache.clear();
        dict = new HashMap<>(); fragKeys = new ArrayList<>(); values = new java.util.HashSet<>();
        if (!"fr".equals(lang)) {
            try (InputStream in = c.getApplicationContext().getAssets().open("i18n.json")) {
                byte[] b = new byte[in.available()]; int n = 0, r;
                while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
                JSONObject all = new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
                JSONObject o = all.optJSONObject(lang);
                if (o != null) for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next(); dict.put(k, o.getString(k)); values.add(o.getString(k));
                    if (fragment(k)) fragKeys.add(k);
                }
                Collections.sort(fragKeys, (a, bb) -> bb.length() - a.length());
            } catch (Exception ignored) {}
        }
        loaded = true;
        Locale.setDefault(new Locale(lang));
    }

    /** Une clé est « fragment » (remplaçable dans un texte plus long) si elle finit/commence par un séparateur ou est longue. */
    private static boolean fragment(String k) {
        if (k.length() < 5) return false;
        return !k.equals(k.trim()) || k.endsWith(":") || k.endsWith("(") || k.endsWith("…") || k.length() >= 18;
    }

    /** Traduit un texte français (exact, sinon par fragments). */
    public static String t(String fr) {
        if (fr == null || !loaded || "fr".equals(lang) || fr.isEmpty()) return fr;
        String d = dict.get(fr);
        if (d != null) return d;
        String c = cache.get(fr);
        if (c != null) return c;
        String out = fr;
        for (String k : fragKeys) if (out.contains(k)) out = out.replace(k, dict.get(k));
        if (cache.size() > 2000) cache.clear();
        cache.put(fr, out);
        return out;
    }

    /** Contexte avec la locale choisie (ressources strings.xml, dates, etc.). */
    public static Context wrap(Context base) {
        if (!loaded) init(base);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        Locale l = new Locale(lang);
        cfg.setLocale(l);
        return base.createConfigurationContext(cfg);
    }

    // ── Traduction des vues ─────────────────────────────────────────────────
    public static void walk(View v) {
        if ("fr".equals(lang) || v == null) return;
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            CharSequence tx = tv.getText();
            if (tx != null && tx.length() > 0 && !(tv instanceof android.widget.EditText)) {
                String s = tx.toString();
                if (!values.contains(s)) { String r = t(s); if (!r.equals(s)) tv.setText(r); }
            }
            CharSequence h = tv.getHint();
            if (h != null && h.length() > 0) { String s = h.toString(); if (!values.contains(s)) { String r = t(s); if (!r.equals(s)) tv.setHint(r); } }
        }
        CharSequence cd = v.getContentDescription();
        if (cd != null && cd.length() > 0) { String s = cd.toString(); if (!values.contains(s)) { String r = t(s); if (!r.equals(s)) v.setContentDescription(r); } }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i)); }
    }

    /** Traduit la vue maintenant, puis à chaque changement de mise en page (listes, textes dynamiques). */
    public static void watch(final View root) {
        if ("fr".equals(lang)) return;
        walk(root);
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable run = () -> walk(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() { h.removeCallbacks(run); h.postDelayed(run, 30); }
        });
    }

    /** Pour un dialogue affiché (titre/boutons/message générés par le système). */
    public static void watchDialog(android.app.Dialog d) {
        if (d == null || d.getWindow() == null || "fr".equals(lang)) return;
        watch(d.getWindow().getDecorView());
    }
}
