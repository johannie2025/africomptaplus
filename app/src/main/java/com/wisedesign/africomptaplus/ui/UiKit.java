package com.wisedesign.africomptaplus.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.db.DatabaseHelper;

import java.io.File;
import java.util.Locale;

/** Petits utilitaires UI partagés (design guide AfriCompta+ Pro). */
public final class UiKit {
    private UiKit() {}

    // ── Formatage XAF (espaces normaux : sûrs pour écran ET PDF) ────────────
    public static String fmt(double v) {
        return String.format(Locale.FRENCH, "%,.0f", v)
                .replace(' ', ' ').replace(' ', ' ');
    }
    /** Devise affichée (chargée au démarrage et lors du choix du pays). */
    public static String currency = "XAF";
    public static String xaf(double v) { return fmt(v) + " " + currency; }

    // ── Barre de titre commune ──────────────────────────────────────────────
    public static void topBar(Activity a, String title) {
        TextView t = a.findViewById(R.id.tvScreenTitle);
        if (t != null) t.setText(title);
        View back = a.findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> a.onBackPressed());
    }

    public static void topAction(Activity a, String label, int iconRes, View.OnClickListener l) {
        View box = a.findViewById(R.id.btnTopAction);
        if (box == null) return;
        ((TextView) a.findViewById(R.id.tvTopAction)).setText(label);
        ((ImageView) a.findViewById(R.id.ivTopAction)).setImageResource(iconRes);
        box.setVisibility(View.VISIBLE);
        box.setOnClickListener(l);
        pressScale(box);
    }

    // ── Animation press : scale 0.97, 200 ms ────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    public static void pressScale(View... views) {
        for (View v : views) {
            if (v == null) continue;
            v.setOnTouchListener((view, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(200).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                        break;
                    default: break;
                }
                return false; // laisse passer le clic
            });
        }
    }

    // ── Initiales d'un nom (avatar) ─────────────────────────────────────────
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        String s = p[0].substring(0, 1);
        if (p.length > 1) s += p[1].substring(0, 1);
        return s.toUpperCase(Locale.getDefault());
    }

    // ── Logo boutique ───────────────────────────────────────────────────────
    public static String logoPath(Context c) {
        String p = DatabaseHelper.getInstance(c).getConfig("shop_logo_path");
        return (p != null && !p.isEmpty() && new File(p).exists()) ? p : "";
    }

    /** Charge le logo (redimensionné, ratio conservé) ou null. */
    public static Bitmap loadLogo(Context c, int maxPx) {
        String path = logoPath(c);
        if (path.isEmpty()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int sample = 1;
        while (o.outWidth / sample > maxPx * 2 || o.outHeight / sample > maxPx * 2) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, o2);
    }
}
