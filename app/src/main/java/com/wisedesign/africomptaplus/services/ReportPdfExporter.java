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
import com.wisedesign.africomptaplus.models.FinancialReport;
import com.wisedesign.africomptaplus.models.ShopConfig;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Export PDF A4 du rapport financier, avec l'identité de la boutique. */
public class ReportPdfExporter {
    private static final int W = 595, H = 842, M = 36;
    private final Context ctx;

    public ReportPdfExporter(Context c) { ctx = c.getApplicationContext(); }

    public Uri export(FinancialReport r, String periodName) {
        ShopConfig cfg = ShopConfig.load(DatabaseHelper.getInstance(ctx));
        PdfDocument doc = new PdfDocument();
        try {
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, 1).create());
            draw(page.getCanvas(), r, periodName, cfg);
            doc.finishPage(page);
            File dir = new File(ctx.getExternalCacheDir(), "invoices");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "rapport_" + periodName.toLowerCase() + "_" + System.currentTimeMillis() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(out)) { doc.writeTo(fos); }
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
        } catch (Exception e) {
            return null;
        } finally { doc.close(); }
    }

    private void draw(Canvas cv, FinancialReport r, String period, ShopConfig cfg) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(PdfStyle.PRIMARY); cv.drawRect(0, 0, W, 8, p);
        float y = PdfStyle.header(cv, cfg, M, 30, W - 2 * M - 160);

        p.setTextAlign(Paint.Align.RIGHT); p.setColor(PdfStyle.PRIMARY);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(18f);
        cv.drawText("RAPPORT FINANCIER", W - M, 46, p);
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(10f); p.setColor(PdfStyle.MUTED);
        cv.drawText(period + " · " + r.periodStart + " → " + r.periodEnd, W - M, 62, p);

        y = Math.max(y, 90) + 14;
        String[][] k = {
                {"Chiffre d'affaires", UiKit.xaf(r.totalRevenue)}, {"Bénéfice brut", UiKit.xaf(r.grossProfit)},
                {"Dépenses", UiKit.xaf(r.totalExpenses)}, {"Bénéfice net", UiKit.xaf(r.netProfit)},
                {"Nombre de ventes", String.valueOf(r.salesCount)}, {"Panier moyen", UiKit.xaf(r.avgBasket)},
                {"Dettes en cours", UiKit.xaf(r.totalDebts)}};
        float cw = (W - 2 * M - 12) / 2f;
        for (int i = 0; i < k.length; i++) {
            float x = M + (i % 2) * (cw + 12), yy = y + (i / 2) * 62;
            p.setColor(PdfStyle.SOFT); cv.drawRoundRect(new RectF(x, yy, x + cw, yy + 52), 10, 10, p);
            p.setTextAlign(Paint.Align.LEFT); p.setColor(PdfStyle.MUTED); p.setTextSize(9.5f);
            cv.drawText(k[i][0], x + 12, yy + 18, p);
            p.setColor(i == 3 ? (r.netProfit >= 0 ? 0xFF10B981 : 0xFFEF4444) : PdfStyle.TEXT);
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(15f);
            cv.drawText(k[i][1], x + 12, yy + 40, p);
            p.setTypeface(Typeface.DEFAULT);
        }
        y += 4 * 62 + 10;

        y = table(cv, p, "Meilleures ventes par produit", r.topProducts, true, y);
        table(cv, p, "Répartition par catégorie", r.topCategories, false, y + 10);

        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(8f); p.setColor(0xFF94A3B8);
        cv.drawText("Généré avec AfriCompta+ · Wise Design", W / 2f, H - 24, p);
    }

    private float table(Canvas cv, Paint p, String title, List<String[]> rows, boolean product, float y) {
        p.setTextAlign(Paint.Align.LEFT); p.setColor(PdfStyle.TEXT);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(12f);
        cv.drawText(title, M, y + 12, p);
        y += 22;
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(10.5f);
        if (rows == null || rows.isEmpty()) {
            p.setColor(PdfStyle.MUTED); cv.drawText("Aucune vente sur la période", M, y + 14, p);
            return y + 24;
        }
        int i = 1;
        for (String[] row : rows) {
            p.setColor(PdfStyle.BORDER); cv.drawRect(M, y + 22, W - M, y + 23, p);
            p.setColor(PdfStyle.TEXT); p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(i++ + ".  " + PdfStyle.ellipsize(p, row[0], 280), M, y + 15, p);
            p.setColor(PdfStyle.MUTED);
            if (product) cv.drawText(row[1] + " vente(s)", M + 330, y + 15, p);
            p.setColor(PdfStyle.PRIMARY); p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.RIGHT);
            double amt = Double.parseDouble(product ? row[2] : row[1]);
            cv.drawText(UiKit.xaf(amt), W - M, y + 15, p);
            p.setTypeface(Typeface.DEFAULT);
            y += 24;
        }
        return y;
    }
}
