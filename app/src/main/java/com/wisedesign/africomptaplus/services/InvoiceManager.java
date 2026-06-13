package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.models.SaleItem;
import com.wisedesign.africomptaplus.models.ShopConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * InvoiceManager V2 :
 *  - Logo boutique personnalisé (si défini dans ShopConfig)
 *  - Nom/contact client sur la facture
 *  - Filigrane "Wise Design +240555445514" si pas de licence
 *  - Config boutique dynamique (nom, adresse, pied de page)
 */
public class InvoiceManager {
    private static final String TAG       = "InvoiceManager";
    private static final int    PAGE_W    = 400;
    private static final int    MARGIN    = 18;
    private static final float  TEXT_XS   = 9f;
    private static final float  TEXT_SM   = 11f;
    private static final float  TEXT_MD   = 13f;
    private static final float  TEXT_LG   = 16f;
    private static final float  TEXT_XL   = 22f;

    private final Context        ctx;
    private final DatabaseHelper db;

    public InvoiceManager(Context context) {
        ctx = context.getApplicationContext();
        db  = DatabaseHelper.getInstance(ctx);
    }

    /** Génère le PDF et retourne l'URI FileProvider. */
    public Uri generateInvoicePDF(Sale sale, List<SaleItem> items) {
        ShopConfig cfg      = loadConfig();
        boolean    licensed = isLicensed();

        PdfDocument      doc  = new PdfDocument();
        PdfDocument.Page page = null;
        FileOutputStream fos  = null;
        try {
            int h = computeHeight(items);
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, h, 1).create();
            page = doc.startPage(info);
            draw(page.getCanvas(), sale, items, cfg, licensed);
            doc.finishPage(page); page = null;

            File dir = new File(ctx.getExternalCacheDir(), "invoices");
            if (!dir.exists()) dir.mkdirs();
            String fname = "facture_" + sale.invoiceNumber.replace("-", "_") + ".pdf";
            File   out   = new File(dir, fname);
            fos = new FileOutputStream(out);
            doc.writeTo(fos);
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
        } catch (IOException e) {
            Log.e(TAG, "PDF error", e);
            return null;
        } finally {
            if (page != null) { try { doc.finishPage(page); } catch (Exception ignored) {} }
            doc.close();
            if (fos != null) { try { fos.close(); } catch (IOException ignored) {} }
        }
    }

    // ── Rendu principal ───────────────────────────────────────────────────────
    private void draw(Canvas cv, Sale sale, List<SaleItem> items, ShopConfig cfg, boolean licensed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Fond blanc
        p.setColor(Color.WHITE);
        cv.drawRect(0, 0, PAGE_W, 9999, p);

        int y = MARGIN;

        // ── LOGO (si défini) ─────────────────────────────────────────────
        if (cfg.logoPath != null && !cfg.logoPath.isEmpty()) {
            Bitmap bmp = BitmapFactory.decodeFile(cfg.logoPath);
            if (bmp != null) {
                int logoW = 80, logoH = 80;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, logoW, logoH, true);
                cv.drawBitmap(scaled, (PAGE_W - logoW) / 2f, y, null);
                y += logoH + 6;
                scaled.recycle(); bmp.recycle();
            }
        }

        // ── EN-TÊTE BOUTIQUE ─────────────────────────────────────────────
        p.setColor(Color.BLACK);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(TEXT_XL);
        p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(cfg.shopName.toUpperCase(), PAGE_W / 2f, y += 28, p);

        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(TEXT_SM);
        if (!cfg.shopPhone.isEmpty()) { cv.drawText(cfg.shopPhone, PAGE_W / 2f, y += 16, p); }
        if (!cfg.shopAddress.isEmpty()) { cv.drawText(cfg.shopAddress, PAGE_W / 2f, y += 14, p); }

        y = dottedLine(cv, p, y + 8);

        // ── INFO FACTURE ─────────────────────────────────────────────────
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Color.DKGRAY);
        p.setTextSize(TEXT_SM);
        cv.drawText("Facture : " + sale.invoiceNumber, MARGIN, y += 16, p);
        cv.drawText("Date    : " + sale.createdAt,     MARGIN, y += 14, p);
        cv.drawText("Paiement: " + fmtPayment(sale.paymentMethod), MARGIN, y += 14, p);

        // ── CLIENT ───────────────────────────────────────────────────────
        p.setColor(Color.BLACK);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        cv.drawText("Client  : " + sale.clientName, MARGIN, y += 14, p);
        if (sale.clientPhone != null && !sale.clientPhone.isEmpty()) {
            p.setTypeface(Typeface.DEFAULT);
            cv.drawText("Tél     : " + sale.clientPhone, MARGIN, y += 12, p);
        }
        p.setTypeface(Typeface.DEFAULT);

        y = dottedLine(cv, p, y + 8);

        // ── COLONNES ─────────────────────────────────────────────────────
        int cProd = MARGIN, cQty = PAGE_W - MARGIN - 110, cTot = PAGE_W - MARGIN;
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(Color.BLACK); p.setTextSize(TEXT_SM);
        p.setTextAlign(Paint.Align.LEFT);
        cv.drawText("Article", cProd, y += 16, p);
        cv.drawText("Qté",     cQty,  y,       p);
        p.setTextAlign(Paint.Align.RIGHT);
        cv.drawText("Total",   cTot,  y,       p);
        y = dottedLine(cv, p, y + 6);

        // ── ARTICLES ─────────────────────────────────────────────────────
        p.setTypeface(Typeface.DEFAULT); p.setColor(Color.BLACK); p.setTextSize(TEXT_SM);
        for (SaleItem item : items) {
            String name = item.productName;
            if (name != null && name.length() > 22) name = name.substring(0, 20) + "..";
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(name != null ? name : "—", cProd, y += 18, p);
            cv.drawText("x" + item.quantity,        cQty,  y,       p);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(fmtAmt(item.totalPrice),    cTot,  y,       p);
            // sous-ligne prix unitaire
            p.setColor(Color.GRAY); p.setTextSize(TEXT_XS); p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(fmtAmt(item.unitPrice) + " / unité", MARGIN + 8, y += 10, p);
            p.setTextSize(TEXT_SM); p.setColor(Color.BLACK);
        }

        y = dottedLine(cv, p, y + 8);

        // ── TOTAL ────────────────────────────────────────────────────────
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(TEXT_LG); p.setColor(Color.BLACK);
        p.setTextAlign(Paint.Align.LEFT);
        cv.drawText("TOTAL", MARGIN, y += 24, p);
        p.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(fmtAmt(sale.totalAmount) + " " + "XAF", cTot, y, p);

        // ── PIED DE PAGE ─────────────────────────────────────────────────
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(TEXT_SM);
        p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.DKGRAY);
        String footer = cfg.invoiceFooter.isEmpty() ? "Merci pour votre achat !" : cfg.invoiceFooter;
        cv.drawText(footer, PAGE_W / 2f, y += 24, p);
        cv.drawText("AfriCompta+ · Wise Design", PAGE_W / 2f, y += 14, p);

        // ── FILIGRANE si pas de licence ───────────────────────────────────
        if (!licensed) {
            drawWatermark(cv);
        }
    }

    /** Filigrane diagonal rouge semi-transparent. */
    private void drawWatermark(Canvas cv) {
        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setColor(Color.argb(55, 220, 0, 0));
        wp.setTextSize(28f);
        wp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        wp.setTextAlign(Paint.Align.CENTER);

        cv.save();
        cv.rotate(-40, PAGE_W / 2f, 400);
        cv.drawText("WISE DESIGN",         PAGE_W / 2f, 340, wp);
        cv.drawText("+240 555 445 514",    PAGE_W / 2f, 376, wp);
        cv.drawText("VERSION DÉMO",        PAGE_W / 2f, 412, wp);
        cv.restore();

        // Deuxième filigrane décalé
        cv.save();
        cv.rotate(-40, PAGE_W / 2f, 700);
        cv.drawText("WISE DESIGN",         PAGE_W / 2f, 640, wp);
        cv.drawText("+240 555 445 514",    PAGE_W / 2f, 676, wp);
        cv.restore();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int computeHeight(List<SaleItem> items) { return 420 + items.size() * 44; }

    private int dottedLine(Canvas cv, Paint p, int y) {
        p.setColor(Color.LTGRAY); p.setTextSize(TEXT_SM); p.setTextAlign(Paint.Align.LEFT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < (PAGE_W - MARGIN * 2) / 6; i++) sb.append("-");
        cv.drawText(sb.toString(), MARGIN, y + 10, p);
        p.setColor(Color.BLACK);
        return y + 12;
    }

    private String fmtAmt(double v) { return String.format(Locale.FRENCH, "%,.0f", v); }

    private String fmtPayment(String m) {
        if (m == null) return "Espèces";
        switch (m) { case "mobile_money": return "Mobile Money"; case "credit": return "Crédit"; default: return "Espèces"; }
    }

    private ShopConfig loadConfig() {
        ShopConfig c = new ShopConfig();
        c.shopName      = db.getConfig("shop_name");
        c.shopPhone     = db.getConfig("shop_phone");
        c.shopAddress   = db.getConfig("shop_address");
        c.logoPath      = db.getConfig("shop_logo_path");
        c.invoiceFooter = db.getConfig("invoice_footer");
        if (c.shopName.isEmpty()) c.shopName = "Ma Boutique";
        return c;
    }

    private boolean isLicensed() {
        android.database.Cursor cur = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_IS_ACTIVATED + " FROM " + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);
        try { return cur.moveToFirst() && cur.getInt(0) == 1; } finally { cur.close(); }
    }
}
