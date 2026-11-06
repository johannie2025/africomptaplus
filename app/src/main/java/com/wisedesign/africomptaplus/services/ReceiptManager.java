package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Debt;
import com.wisedesign.africomptaplus.models.DebtPayment;
import com.wisedesign.africomptaplus.models.ShopConfig;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.io.File;
import java.io.FileOutputStream;

/** Reçu de paiement de dette (PDF, 420 pt) avec montant en lettres et solde restant. */
public class ReceiptManager {
    private static final int W = 420, M = 24;
    private final Context ctx;
    private final DatabaseHelper db;
    private final DebtService debts;

    public ReceiptManager(Context c) {
        ctx = c.getApplicationContext();
        db = DatabaseHelper.getInstance(ctx);
        debts = new DebtService(ctx);
    }

    public Uri generate(long paymentId) {
        DebtPayment pay = debts.findPayment(paymentId);
        if (pay == null) return null;
        Debt debt = debts.findById(pay.debtId);
        if (debt == null) return null;
        ShopConfig cfg = ShopConfig.load(db);
        double paidAfter = debts.paidUpTo(debt.id, pay.id);
        double remaining = Math.max(0, debt.amount - paidAfter);

        PdfDocument doc = new PdfDocument();
        try {
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, 560, 1).create());
            Canvas cv = page.getCanvas();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF); cv.drawRect(0, 0, W, 560, p);
            p.setColor(PdfStyle.PRIMARY); cv.drawRect(0, 0, W, 6, p);
            float y = PdfStyle.header(cv, cfg, M, 26, W - 2 * M) + 8;

            // Titre
            p.setColor(PdfStyle.SOFT);
            cv.drawRoundRect(new RectF(M, y, W - M, y + 54), 10, 10, p);
            p.setColor(PdfStyle.PRIMARY); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(15f); p.setTextAlign(Paint.Align.LEFT);
            cv.drawText("REÇU DE PAIEMENT", M + 12, y + 22, p);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(10f); p.setColor(PdfStyle.TEXT);
            cv.drawText("N° REC-" + String.format(java.util.Locale.US, "%05d", pay.id), M + 12, y + 40, p);
            p.setTextAlign(Paint.Align.RIGHT); p.setColor(PdfStyle.MUTED);
            cv.drawText(pay.createdAt, W - M - 12, y + 22, p);
            cv.drawText("Mode : " + ("mobile_money".equals(pay.method) ? "Mobile Money" : "Espèces"), W - M - 12, y + 40, p);
            y += 54 + 20;

            p.setTextAlign(Paint.Align.LEFT); p.setColor(PdfStyle.MUTED); p.setTextSize(9f);
            cv.drawText("REÇU DE", M, y, p);
            p.setColor(PdfStyle.TEXT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(13f);
            y += 16; cv.drawText(PdfStyle.ellipsize(p, debt.clientName != null ? debt.clientName : "—", W - 2 * M), M, y, p);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(10f); p.setColor(PdfStyle.MUTED);
            if (debt.notes != null && !debt.notes.isEmpty()) { y += 14; cv.drawText("Réf. : " + PdfStyle.ellipsize(p, debt.notes, W - 2 * M - 40), M, y, p); }
            y += 26;

            // Montant reçu
            p.setColor(PdfStyle.PRIMARY);
            cv.drawRoundRect(new RectF(M, y, W - M, y + 56), 12, 12, p);
            p.setColor(0xFFFFFFFF); p.setTextSize(10f); p.setTextAlign(Paint.Align.LEFT);
            cv.drawText("MONTANT REÇU", M + 14, y + 22, p);
            p.setTextAlign(Paint.Align.RIGHT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(17f);
            cv.drawText(UiKit.fmt(pay.amount) + " " + cfg.currency, W - M - 14, y + 38, p);
            y += 56 + 16;

            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)); p.setTextSize(10f);
            p.setColor(PdfStyle.TEXT); p.setTextAlign(Paint.Align.LEFT);
            String words = "Arrêté le présent reçu à la somme de : " + AmountWords.amount(pay.amount, cfg.currency) + ".";
            for (String ln : PdfStyle.wrap(p, words, W - 2 * M)) { cv.drawText(ln, M, y, p); y += 14; }
            y += 14;

            // Récap dette
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(11f);
            y = row(cv, p, y, "Montant de la dette", UiKit.fmt(debt.amount) + " " + cfg.currency, PdfStyle.TEXT, false);
            y = row(cv, p, y, "Total déjà payé", UiKit.fmt(paidAfter) + " " + cfg.currency, PdfStyle.TEXT, false);
            p.setColor(PdfStyle.BORDER); cv.drawRect(M, y - 6, W - M, y - 5, p);
            y = row(cv, p, y + 4, "Reste à payer", UiKit.fmt(remaining) + " " + cfg.currency, remaining > 0 ? 0xFFF97316 : 0xFF10B981, true);
            if (remaining <= 0) {
                p.setColor(0xFF10B981); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(11f); p.setTextAlign(Paint.Align.RIGHT);
                cv.drawText("DETTE SOLDÉE", W - M, y + 4, p); y += 16;
            }

            // Signatures
            y += 30;
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(9f); p.setColor(PdfStyle.MUTED); p.setTextAlign(Paint.Align.LEFT);
            cv.drawText("Signature du client", M, y, p);
            p.setTextAlign(Paint.Align.RIGHT); cv.drawText("Cachet / signature", W - M, y, p);
            p.setColor(PdfStyle.BORDER);
            cv.drawRect(M, y + 46, M + 130, y + 47, p); cv.drawRect(W - M - 130, y + 46, W - M, y + 47, p);

            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(11f); p.setColor(PdfStyle.TEXT);
            cv.drawText(PdfStyle.ellipsize(p, cfg.invoiceFooter, W - 2 * M), W / 2f, 530, p);
            p.setTextSize(8f); p.setColor(0xFF94A3B8);
            cv.drawText("Généré avec AfriCompta+ · Wise Design", W / 2f, 546, p);

            if (!licensed()) {
                Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
                wp.setColor(0x37DC0000); wp.setTextSize(26f); wp.setTypeface(Typeface.DEFAULT_BOLD); wp.setTextAlign(Paint.Align.CENTER);
                cv.save(); cv.rotate(-35, W / 2f, 280); cv.drawText("VERSION DÉMO", W / 2f, 280, wp); cv.restore();
            }
            doc.finishPage(page);

            File dir = new File(ctx.getExternalCacheDir(), "invoices");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "recu_REC_" + pay.id + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(out)) { doc.writeTo(fos); }
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
        } catch (Exception e) {
            return null;
        } finally { doc.close(); }
    }

    private float row(Canvas cv, Paint p, float y, String label, String value, int color, boolean bold) {
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); p.setTextSize(11f);
        p.setColor(PdfStyle.MUTED); p.setTextAlign(Paint.Align.LEFT); cv.drawText(label, M, y, p);
        p.setColor(color); p.setTextAlign(Paint.Align.RIGHT); cv.drawText(value, W - M, y, p);
        return y + 20;
    }

    private boolean licensed() {
        android.database.Cursor cur = db.rawQuery("SELECT " + DatabaseHelper.COL_IS_ACTIVATED + " FROM " + DatabaseHelper.T_APP_SECURITY + " LIMIT 1", null);
        try { return cur.moveToFirst() && cur.getInt(0) == 1; } finally { cur.close(); }
    }
}
