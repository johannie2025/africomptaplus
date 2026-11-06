package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.models.SaleItem;
import com.wisedesign.africomptaplus.models.ShopConfig;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Facture PDF professionnelle (format A6 étendu, 420 pt).
 * Logo + identité boutique (nom, activité, adresse, tél, email, N° fiscal, pied de page)
 * viennent des Paramètres. Filigrane si licence non activée.
 */
public class InvoiceManager {
    private static final String TAG = "InvoiceManager";
    private static final int W = 420, M = 24;

    private final Context ctx;
    private final DatabaseHelper db;

    public InvoiceManager(Context context) {
        ctx = context.getApplicationContext();
        db  = DatabaseHelper.getInstance(ctx);
    }

    /** Paramètres d'affichage selon le type de document. */
    static class Meta { String title, rightLine, clientLabel, intro; boolean invoice; }

    public Uri generateInvoicePDF(Sale sale, List<SaleItem> items) {
        Meta m = new Meta();
        m.title = "FACTURE"; m.rightLine = "Paiement : " + payment(sale.paymentMethod);
        m.clientLabel = "FACTURÉ À"; m.intro = "Arrêtée la présente facture"; m.invoice = true;
        return render(sale, items, m, "facture");
    }

    /** Devis ou facture proforma (mêmes mise en page, libellés adaptés, aucune mention de paiement). */
    public Uri generateDocumentPDF(com.wisedesign.africomptaplus.models.Document d) {
        Sale s = new Sale();
        s.invoiceNumber = d.number; s.createdAt = d.createdAt; s.clientName = d.clientName; s.clientPhone = d.clientPhone;
        s.subtotal = d.subtotal; s.taxRate = d.taxRate; s.taxAmount = d.taxAmount; s.totalAmount = d.totalAmount;
        boolean pro = com.wisedesign.africomptaplus.models.Document.PROFORMA.equals(d.docType);
        Meta m = new Meta();
        m.title = pro ? "FACTURE PROFORMA" : "DEVIS";
        m.rightLine = (d.validUntil != null && d.validUntil.length() >= 10)
                ? "Valable jusqu'au " + d.validUntil.substring(8, 10) + "/" + d.validUntil.substring(5, 7) + "/" + d.validUntil.substring(0, 4) : "";
        m.clientLabel = "DESTINATAIRE";
        m.intro = pro ? "Arrêtée la présente facture proforma" : "Arrêté le présent devis";
        m.invoice = false;
        return render(s, d.items, m, pro ? "proforma" : "devis");
    }

    private Uri render(Sale sale, List<SaleItem> items, Meta m, String filePrefix) {
        ShopConfig cfg = ShopConfig.load(db);
        boolean licensed = isLicensed();

        PdfDocument doc = new PdfDocument();
        FileOutputStream fos = null;
        try {
            int h = (int) draw(new Canvas(), sale, items, cfg, licensed, true, m) + 30;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, h, 1).create());
            draw(page.getCanvas(), sale, items, cfg, licensed, false, m);
            doc.finishPage(page);

            File dir = new File(ctx.getExternalCacheDir(), "invoices");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, filePrefix + "_" + sale.invoiceNumber.replace("-", "_") + ".pdf");
            fos = new FileOutputStream(out);
            doc.writeTo(fos);
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
        } catch (IOException e) {
            Log.e(TAG, "PDF error", e);
            return null;
        } finally {
            doc.close();
            if (fos != null) { try { fos.close(); } catch (IOException ignored) {} }
        }
    }

    /** Dessine la facture ; retourne le y final (utilisé en mode "dry" pour calculer la hauteur). */
    private float draw(Canvas cv, Sale sale, List<SaleItem> items, ShopConfig cfg, boolean licensed, boolean dry, Meta m) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!dry) { p.setColor(0xFFFFFFFF); cv.drawRect(0, 0, W, 5000, p); }

        // Bande supérieure
        p.setColor(PdfStyle.PRIMARY);
        cv.drawRect(0, 0, W, 6, p);

        float y = PdfStyle.header(cv, cfg, M, 26, W - 2 * M);

        // Titre facture
        y += 8;
        p.setColor(PdfStyle.SOFT);
        cv.drawRoundRect(new RectF(M, y, W - M, y + 54), 10, 10, p);
        p.setColor(PdfStyle.PRIMARY); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(15f);
        p.setTextAlign(Paint.Align.LEFT);
        cv.drawText(m.title, M + 12, y + 22, p);
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(10f); p.setColor(PdfStyle.TEXT);
        cv.drawText("N° " + sale.invoiceNumber, M + 12, y + 40, p);
        p.setTextAlign(Paint.Align.RIGHT); p.setColor(PdfStyle.MUTED);
        cv.drawText(sale.createdAt != null ? sale.createdAt : "", W - M - 12, y + 22, p);
        cv.drawText(m.rightLine, W - M - 12, y + 40, p);
        y += 54 + 18;

        // Client
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(PdfStyle.MUTED); p.setTextSize(9f);
        cv.drawText(m.clientLabel, M, y, p);
        p.setColor(PdfStyle.TEXT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(12f);
        y += 15;
        cv.drawText(PdfStyle.ellipsize(p, sale.clientName != null ? sale.clientName : "Client Passager", W - 2 * M), M, y, p);
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(10f); p.setColor(PdfStyle.MUTED);
        if (sale.clientPhone != null && !sale.clientPhone.isEmpty()) { y += 13; cv.drawText(sale.clientPhone, M, y, p); }
        y += 18;

        // En-tête tableau
        p.setColor(PdfStyle.PRIMARY);
        cv.drawRoundRect(new RectF(M, y, W - M, y + 24), 8, 8, p);
        p.setColor(0xFFFFFFFF); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(10f);
        float cQty = W - M - 150, cPu = W - M - 90, cTot = W - M - 8;
        p.setTextAlign(Paint.Align.LEFT);  cv.drawText("Article", M + 8, y + 16, p);
        p.setTextAlign(Paint.Align.CENTER); cv.drawText("Qté", cQty, y + 16, p);
        p.setTextAlign(Paint.Align.RIGHT);  cv.drawText("P.U.", cPu + 22, y + 16, p);
        cv.drawText("Total", cTot, y + 16, p);
        y += 24;

        // Lignes
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(10.5f);
        int i = 0;
        for (SaleItem it : items) {
            if (i++ % 2 == 1) { p.setColor(0xFFF8FAFC); cv.drawRect(M, y, W - M, y + 26, p); }
            p.setColor(PdfStyle.TEXT);
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(PdfStyle.ellipsize(p, it.productName != null ? it.productName : "—", cQty - M - 28), M + 8, y + 17, p);
            p.setTextAlign(Paint.Align.CENTER); cv.drawText(String.valueOf(it.quantity), cQty, y + 17, p);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(UiKit.fmt(it.unitPrice), cPu + 22, y + 17, p);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            cv.drawText(UiKit.fmt(it.totalPrice), cTot, y + 17, p);
            p.setTypeface(Typeface.DEFAULT);
            y += 26;
        }
        p.setColor(PdfStyle.BORDER); cv.drawRect(M, y, W - M, y + 1, p);
        y += 16;

        // Sous-total / TVA (si activée sur cette vente)
        if (sale.taxRate > 0) {
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(10.5f); p.setColor(PdfStyle.MUTED);
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText("Sous-total HT", W - M - 200, y + 4, p);
            cv.drawText("TVA (" + (sale.taxRate == Math.floor(sale.taxRate) ? String.valueOf((long) sale.taxRate) : String.valueOf(sale.taxRate)) + " %)", W - M - 200, y + 20, p);
            p.setTextAlign(Paint.Align.RIGHT); p.setColor(PdfStyle.TEXT);
            cv.drawText(UiKit.fmt(sale.subtotal), W - M, y + 4, p);
            cv.drawText(UiKit.fmt(sale.taxAmount), W - M, y + 20, p);
            y += 34;
        }

        // Total
        p.setColor(PdfStyle.PRIMARY);
        cv.drawRoundRect(new RectF(W - M - 200, y, W - M, y + 40), 12, 12, p);
        p.setColor(0xFFFFFFFF); p.setTextAlign(Paint.Align.LEFT); p.setTextSize(10f);
        cv.drawText("TOTAL", W - M - 188, y + 24, p);
        p.setTextAlign(Paint.Align.RIGHT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(15f);
        cv.drawText(UiKit.fmt(sale.totalAmount) + " " + cfg.currency, W - M - 12, y + 26, p);
        y += 40 + 10;

        if (m.invoice && "credit".equals(sale.paymentMethod)) {
            p.setColor(0xFFF59E0B); p.setTextSize(10f); p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText("VENTE À CRÉDIT — solde à régler", W - M, y + 8, p);
            y += 14;
        }

        // Montant en lettres
        y += 14;
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)); p.setTextSize(9.5f);
        p.setColor(PdfStyle.MUTED); p.setTextAlign(Paint.Align.LEFT);
        String words = m.intro + " à la somme de : " + AmountWords.amount(sale.totalAmount, cfg.currency) + ".";
        for (String ln : PdfStyle.wrap(p, words, W - 2 * M)) { cv.drawText(ln, M, y, p); y += 13; }

        // Pied de page
        y += 14;
        p.setTypeface(Typeface.DEFAULT); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(11f);
        p.setColor(PdfStyle.TEXT);
        cv.drawText(PdfStyle.ellipsize(p, cfg.invoiceFooter, W - 2 * M), W / 2f, y, p);
        y += 14;
        p.setTextSize(8f); p.setColor(0xFF94A3B8);
        cv.drawText("Généré avec AfriCompta+ · Wise Design", W / 2f, y, p);

        if (!licensed && !dry) watermark(cv, y);
        return y;
    }

    private void watermark(Canvas cv, float height) {
        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setColor(0x37DC0000); wp.setTextSize(26f);
        wp.setTypeface(Typeface.DEFAULT_BOLD); wp.setTextAlign(Paint.Align.CENTER);
        cv.save();
        cv.rotate(-35, W / 2f, height / 2f);
        cv.drawText("VERSION DÉMO", W / 2f, height / 2f - 20, wp);
        cv.drawText("WISE DESIGN", W / 2f, height / 2f + 12, wp);
        cv.drawText("+240 222 886 007", W / 2f, height / 2f + 44, wp);
        cv.restore();
    }

    private String payment(String m) {
        if (m == null) return "Espèces";
        switch (m) { case "mobile_money": return "Mobile Money"; case "credit": return "Crédit"; default: return "Espèces"; }
    }

    private boolean isLicensed() {
        android.database.Cursor cur = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_IS_ACTIVATED + " FROM " + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);
        try { return cur.moveToFirst() && cur.getInt(0) == 1; } finally { cur.close(); }
    }
}
