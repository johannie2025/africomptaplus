package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import android.net.Uri;

import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.models.SaleItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * InvoiceManager — Générateur de facture PDF natif (Android graphics.pdf.PdfDocument).
 * Simule un ticket thermique 80mm.
 * Largeur cible : 80mm ≈ 227pt à 72dpi (on utilise 300px pour lisibilité).
 */
public class InvoiceManager {

    private static final String TAG = "InvoiceManager";

    // ── Dimensions du "ticket thermique" simulé ───────────────────────────────
    private static final int PAGE_WIDTH  = 380;  // ~80mm en pixels (screen density)
    private static final int MARGIN      = 16;
    private static final int CONTENT_W   = PAGE_WIDTH - (MARGIN * 2);

    // ── Police principale ─────────────────────────────────────────────────────
    private static final float TEXT_SM   = 11f;
    private static final float TEXT_MD   = 13f;
    private static final float TEXT_LG   = 16f;
    private static final float TEXT_XL   = 20f;

    private final Context context;

    public InvoiceManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Génère un fichier PDF de facture et retourne son URI (FileProvider).
     *
     * @param sale  objet Sale (avec invoiceNumber, total, paymentMethod, createdAt)
     * @param items liste des SaleItem (avec productName, qty, unitPrice, totalPrice)
     * @return Uri du fichier PDF, ou null si erreur
     */
    public Uri generateInvoicePDF(Sale sale, List<SaleItem> items) {
        PdfDocument       document = new PdfDocument();
        PdfDocument.Page  page     = null;
        FileOutputStream  fos      = null;

        try {
            // ── Calcul de la hauteur dynamique du ticket ──────────────────
            int estimatedHeight = 340 + (items.size() * 42);
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, estimatedHeight, 1).create();
            page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            drawTicket(canvas, sale, items);

            document.finishPage(page);
            page = null;

            // ── Sauvegarde dans le cache externe ──────────────────────────
            File outDir = new File(context.getExternalCacheDir(), "invoices");
            if (!outDir.exists()) outDir.mkdirs();

            String fileName = "facture_" + sale.invoiceNumber.replace("-", "_") + ".pdf";
            File   outFile  = new File(outDir, fileName);

            fos = new FileOutputStream(outFile);
            document.writeTo(fos);

            // ── FileProvider URI pour partage natif ───────────────────────
            return FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    outFile);

        } catch (IOException e) {
            Log.e(TAG, "Erreur génération PDF", e);
            return null;
        } finally {
            if (page != null) {
                try { document.finishPage(page); } catch (Exception ignored) {}
            }
            document.close();
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ── Rendu du ticket ───────────────────────────────────────────────────────

    private void drawTicket(Canvas canvas, Sale sale, List<SaleItem> items) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int y = MARGIN;

        // Fond blanc
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, PAGE_WIDTH, 9999, paint);

        // ── En-tête ───────────────────────────────────────────────────────
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(TEXT_XL);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("AFRICOMPTA+", PAGE_WIDTH / 2f, y += 28, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(TEXT_SM);
        canvas.drawText("Wise Design", PAGE_WIDTH / 2f, y += 18, paint);
        canvas.drawText("WhatsApp : +240 555 445 514", PAGE_WIDTH / 2f, y += 16, paint);

        // Ligne séparatrice
        y = drawDottedLine(canvas, paint, y + 10);

        // ── Métadonnées de la facture ─────────────────────────────────────
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(TEXT_SM);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(Color.DKGRAY);

        canvas.drawText("Facture : " + sale.invoiceNumber, MARGIN, y += 16, paint);
        canvas.drawText("Date    : " + sale.createdAt,     MARGIN, y += 14, paint);
        canvas.drawText("Paiement: " + formatPayment(sale.paymentMethod), MARGIN, y += 14, paint);

        // Ligne séparatrice
        y = drawDottedLine(canvas, paint, y + 8);

        // ── En-tête colonnes ──────────────────────────────────────────────
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(TEXT_SM);

        int colProduct = MARGIN;
        int colQty     = PAGE_WIDTH - MARGIN - 110;
        int colTotal   = PAGE_WIDTH - MARGIN;

        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Article",   colProduct, y += 16, paint);
        canvas.drawText("Qté",       colQty,     y,       paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Total",     colTotal,   y,       paint);

        y = drawDottedLine(canvas, paint, y + 6);

        // ── Lignes d'articles ─────────────────────────────────────────────
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(TEXT_SM);
        paint.setColor(Color.BLACK);

        for (SaleItem item : items) {
            // Nom du produit (tronqué si trop long)
            String name = item.productName;
            if (name != null && name.length() > 20) name = name.substring(0, 18) + "..";

            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(name != null ? name : "—", colProduct, y += 18, paint);
            canvas.drawText("x" + item.quantity,       colQty,     y,       paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(formatAmount(item.totalPrice), colTotal, y, paint);

            // Prix unitaire (ligne secondaire)
            paint.setColor(Color.GRAY);
            paint.setTextSize(9f);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(formatAmount(item.unitPrice) + " / unité", MARGIN + 8, y += 11, paint);
            paint.setTextSize(TEXT_SM);
            paint.setColor(Color.BLACK);
        }

        // Ligne séparatrice
        y = drawDottedLine(canvas, paint, y + 8);

        // ── TOTAL GÉNÉRAL (en gras, grand) ────────────────────────────────
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(TEXT_LG);
        paint.setColor(Color.BLACK);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("TOTAL", MARGIN, y += 22, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatAmount(sale.totalAmount) + " XAF", colTotal, y, paint);

        // ── Pied de page ──────────────────────────────────────────────────
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(TEXT_SM);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("Merci pour votre achat !", PAGE_WIDTH / 2f, y += 24, paint);
        canvas.drawText("— AfriCompta+ by Wise Design —", PAGE_WIDTH / 2f, y += 14, paint);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /** Trace une ligne en pointillés et retourne le nouveau Y. */
    private int drawDottedLine(Canvas canvas, Paint paint, int y) {
        paint.setColor(Color.LTGRAY);
        paint.setTextSize(TEXT_SM);
        paint.setTextAlign(Paint.Align.LEFT);

        StringBuilder dots = new StringBuilder();
        int approxChars = CONTENT_W / 6;
        for (int i = 0; i < approxChars; i++) dots.append("-");
        canvas.drawText(dots.toString(), MARGIN, y + 10, paint);

        paint.setColor(Color.BLACK);
        return y + 12;
    }

    private String formatAmount(double amount) {
        return String.format(Locale.FRENCH, "%,.0f", amount);
    }

    private String formatPayment(String method) {
        if (method == null) return "Espèces";
        switch (method) {
            case "mobile_money": return "Mobile Money";
            case "credit":       return "Crédit";
            default:             return "Espèces";
        }
    }
}
