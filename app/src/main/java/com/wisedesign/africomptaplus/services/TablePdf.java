package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.ShopConfig;

import java.io.File;
import java.io.FileOutputStream;

/** Export PDF A4 paginé de n'importe quel ReportTable (journal, grand livre, balance, bilan…). */
public final class TablePdf {
    private TablePdf() {}
    private static final int W = 595, H = 842, M = 28, ROW = 15, BOTTOM = 40;

    public static Uri export(Context ctx, ReportTable t, String subtitle, String fileName) {
        ShopConfig cfg = ShopConfig.load(DatabaseHelper.getInstance(ctx));
        PdfDocument doc = new PdfDocument();
        try {
            int pageNo = 1;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
            Canvas cv = page.getCanvas();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float y = header(cv, p, cfg, t, subtitle);
            y = tableHeader(cv, p, t, y);
            float sum = 0; for (float w : t.weights) sum += w;
            float cw = W - 2 * M;
            for (int i = 0; i < t.rows.size(); i++) {
                if (y + ROW > H - BOTTOM) {
                    footer(cv, p, pageNo); doc.finishPage(page);
                    page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++pageNo).create());
                    cv = page.getCanvas(); y = M + 10; y = tableHeader(cv, p, t, y);
                }
                boolean b = t.bold.get(i);
                if (b) { p.setColor(0xFFF1F5F9); cv.drawRect(M, y, W - M, y + ROW, p); }
                p.setTypeface(b ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); p.setTextSize(8f); p.setColor(PdfStyle.TEXT);
                float x = M;
                String[] r = t.rows.get(i);
                for (int c = 0; c < r.length; c++) {
                    float w = cw * t.weights[c] / sum;
                    String s = PdfStyle.ellipsize(p, r[c], w - 6);
                    if (t.right[c]) { p.setTextAlign(Paint.Align.RIGHT); cv.drawText(s, x + w - 3, y + 10.5f, p); }
                    else { p.setTextAlign(Paint.Align.LEFT); cv.drawText(s, x + 3, y + 10.5f, p); }
                    x += w;
                }
                p.setColor(PdfStyle.BORDER); cv.drawRect(M, y + ROW - 0.5f, W - M, y + ROW, p);
                y += ROW;
            }
            footer(cv, p, pageNo); doc.finishPage(page);
            File dir = new File(ctx.getExternalCacheDir(), "invoices");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, fileName + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(out)) { doc.writeTo(fos); }
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);
        } catch (Exception e) { return null; }
        finally { doc.close(); }
    }

    private static float header(Canvas cv, Paint p, ShopConfig cfg, ReportTable t, String subtitle) {
        p.setColor(PdfStyle.PRIMARY); cv.drawRect(0, 0, W, 6, p);
        float y = PdfStyle.header(cv, cfg, M, 22, W - 2 * M) + 6;
        p.setColor(PdfStyle.PRIMARY); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(15f); p.setTextAlign(Paint.Align.LEFT);
        cv.drawText(t.title, M, y + 12, p);
        p.setColor(PdfStyle.MUTED); p.setTypeface(Typeface.DEFAULT); p.setTextSize(9f);
        cv.drawText(subtitle, M, y + 26, p);
        return y + 38;
    }

    private static float tableHeader(Canvas cv, Paint p, ReportTable t, float y) {
        p.setColor(PdfStyle.PRIMARY); cv.drawRect(M, y, W - M, y + 18, p);
        p.setColor(0xFFFFFFFF); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(8.5f);
        float sum = 0; for (float w : t.weights) sum += w;
        float x = M, cw = W - 2 * M;
        for (int c = 0; c < t.headers.length; c++) {
            float w = cw * t.weights[c] / sum;
            if (t.right[c]) { p.setTextAlign(Paint.Align.RIGHT); cv.drawText(t.headers[c], x + w - 3, y + 12, p); }
            else { p.setTextAlign(Paint.Align.LEFT); cv.drawText(t.headers[c], x + 3, y + 12, p); }
            x += w;
        }
        return y + 18;
    }

    private static void footer(Canvas cv, Paint p, int n) {
        p.setColor(0xFF94A3B8); p.setTypeface(Typeface.DEFAULT); p.setTextSize(8f); p.setTextAlign(Paint.Align.CENTER);
        cv.drawText("AfriCompta+ · Wise Design — page " + n, W / 2f, H - 20, p);
    }
}
