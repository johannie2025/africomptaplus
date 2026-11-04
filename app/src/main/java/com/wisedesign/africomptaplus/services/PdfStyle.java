package com.wisedesign.africomptaplus.services;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.wisedesign.africomptaplus.models.ShopConfig;

import java.io.File;

/** Dessin partagé de l'en-tête boutique (logo + coordonnées) pour factures et rapports. */
final class PdfStyle {
    static final int PRIMARY = 0xFF1E40AF, TEXT = 0xFF0F172A, MUTED = 0xFF64748B,
            BORDER = 0xFFE2E8F0, SOFT = 0xFFF1F5F9;
    private PdfStyle() {}

    static Bitmap logo(ShopConfig cfg, int maxPx) {
        if (cfg.logoPath == null || cfg.logoPath.isEmpty() || !new File(cfg.logoPath).exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(cfg.logoPath, o);
        int s = 1;
        while (o.outWidth / s > maxPx * 2 || o.outHeight / s > maxPx * 2) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        return BitmapFactory.decodeFile(cfg.logoPath, o2);
    }

    /** Dessine logo + nom + coordonnées à partir de (x,y) ; retourne le y final. */
    static float header(Canvas cv, ShopConfig cfg, float x, float y, float maxW) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float textX = x;
        float top = y;
        Bitmap bmp = logo(cfg, 120);
        if (bmp != null) {
            float box = 60f, r = Math.min(box / bmp.getWidth(), box / bmp.getHeight());
            float w = bmp.getWidth() * r, h = bmp.getHeight() * r;
            cv.drawBitmap(bmp, null, new RectF(x, y, x + w, y + h), new Paint(Paint.FILTER_BITMAP_FLAG));
            textX = x + w + 12;
            bmp.recycle();
        }
        float ty = y + 14;
        p.setColor(TEXT); p.setFakeBoldText(true); p.setTextSize(16f);
        cv.drawText(ellipsize(p, cfg.shopName.toUpperCase(), maxW - (textX - x)), textX, ty, p);
        p.setFakeBoldText(false); p.setTextSize(9.5f); p.setColor(MUTED);
        String[] lines = {cfg.tagline, cfg.shopAddress, cfg.shopPhone, cfg.shopEmail,
                cfg.taxId.isEmpty() ? "" : "N° fiscal : " + cfg.taxId};
        for (String l : lines) {
            if (l == null || l.isEmpty()) continue;
            ty += 12.5f;
            cv.drawText(ellipsize(p, l, maxW - (textX - x)), textX, ty, p);
        }
        return Math.max(ty, top + (bmp != null ? 60f : 0f)) + 6;
    }

    static String ellipsize(Paint p, String s, float w) {
        if (s == null) return "";
        if (p.measureText(s) <= w) return s;
        while (s.length() > 1 && p.measureText(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
