package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.FinancialReport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportPdfManager {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;

    private final Context context;
    private final DatabaseHelper db;

    public ReportPdfManager(Context context) {
        this.context = context.getApplicationContext();
        db = DatabaseHelper.getInstance(context);
    }

    public Uri generate(FinancialReport report, String start, String end) {
        PdfDocument document = new PdfDocument();
        FileOutputStream output = null;
        try {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            draw(page.getCanvas(), report, start, end);
            document.finishPage(page);

            File directory = new File(context.getCacheDir(), "reports");
            if (!directory.exists() && !directory.mkdirs()) return null;
            String fileName = "rapport_" + start + "_" + end + ".pdf";
            File file = new File(directory, fileName);
            output = new FileOutputStream(file);
            document.writeTo(output);
            return FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        } finally {
            document.close();
            if (output != null) {
                try { output.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void draw(Canvas canvas, FinancialReport report, String start, String end) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawColor(Color.WHITE);

        String logoPath = db.getConfig("shop_logo_path");
        Bitmap logo = logoPath == null || logoPath.isEmpty() ? null : BitmapFactory.decodeFile(logoPath);
        int y = 44;
        if (logo != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(logo, 58, 58, true);
            canvas.drawBitmap(scaled, 40, y, paint);
            scaled.recycle();
            logo.recycle();
            y += 68;
        }

        paint.setColor(Color.rgb(26, 78, 70));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(22);
        canvas.drawText(valueOr(db.getConfig("shop_name"), context.getString(R.string.shop_default_name)), 40, y, paint);
        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(11);
        String phone = db.getConfig("shop_phone");
        String address = db.getConfig("shop_address");
        if (phone != null && !phone.isEmpty()) canvas.drawText(phone, 40, y + 18, paint);
        if (address != null && !address.isEmpty()) canvas.drawText(address, 40, y + 34, paint);

        y += 66;
        paint.setColor(Color.rgb(26, 78, 70));
        canvas.drawRect(40, y, PAGE_WIDTH - 40, y + 2, paint);
        paint.setTextSize(18);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(context.getString(R.string.report_pdf_heading), 40, y + 30, paint);
        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(12);
        canvas.drawText(context.getString(R.string.report_pdf_period, start, end), 40, y + 52, paint);
        y += 80;

        y = drawMetric(canvas, paint, context.getString(R.string.report_pdf_revenue), amount(report.totalRevenue), y);
        y = drawMetric(canvas, paint, context.getString(R.string.report_pdf_gross_profit), amount(report.grossProfit), y);
        y = drawMetric(canvas, paint, context.getString(R.string.report_pdf_expenses), amount(report.totalExpenses), y);
        y = drawMetric(canvas, paint, context.getString(R.string.report_pdf_net_profit), amount(report.netProfit), y);
        y = drawMetric(canvas, paint, context.getString(R.string.report_pdf_sales_count), String.valueOf(report.salesCount), y);
        y = drawMetric(canvas, paint, context.getString(R.string.report_pdf_average_basket), amount(report.avgBasket), y);
        y = drawMetric(canvas, paint, context.getString(R.string.report_pdf_debts), amount(report.totalDebts), y);

        y += 14;
        y = drawSection(canvas, paint, context.getString(R.string.report_pdf_top_products), report.topProducts, true, y);
        y += 8;
        drawSection(canvas, paint, context.getString(R.string.report_pdf_categories), report.topCategories, false, y);

        paint.setColor(Color.GRAY);
        paint.setTextSize(9);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(context.getString(R.string.report_pdf_generated,
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date())),
                PAGE_WIDTH / 2f, PAGE_HEIGHT - 24, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private int drawMetric(Canvas canvas, Paint paint, String label, String value, int y) {
        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(12);
        canvas.drawText(label, 48, y, paint);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(value, PAGE_WIDTH - 48, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.LTGRAY);
        canvas.drawLine(48, y + 8, PAGE_WIDTH - 48, y + 8, paint);
        return y + 28;
    }

    private int drawSection(Canvas canvas, Paint paint, String title,
                            List<String[]> rows, boolean products, int y) {
        paint.setColor(Color.rgb(26, 78, 70));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(12);
        canvas.drawText(title, 40, y, paint);
        y += 20;
        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(10);
        if (rows == null || rows.isEmpty()) {
            canvas.drawText(context.getString(R.string.report_pdf_no_data), 48, y, paint);
            return y + 16;
        }
        for (String[] row : rows) {
            if (y > PAGE_HEIGHT - 45) break;
            String name = row[0] == null ? "—" : row[0];
            if (!products) name = localizeCategory(name);
            if (name.length() > 38) name = name.substring(0, 35) + "...";
            String detail = products
                    ? "x" + row[1] + "  ·  " + amount(parseAmount(row[2]))
                    : amount(parseAmount(row[1]));
            canvas.drawText(name, 48, y, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(detail, PAGE_WIDTH - 48, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            y += 17;
        }
        return y;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String localizeCategory(String value) {
        String[] stored = {"Loyer", "Personnel", "Transport", "Stock", "Électricité",
                "Eau", "Téléphone/Internet", "Publicité", "Fournitures", "Autre"};
        String[] translated = context.getResources().getStringArray(R.array.expense_categories);
        for (int i = 0; i < stored.length; i++) if (stored[i].equals(value)) return translated[i];
        return value;
    }

    private String amount(double value) {
        return String.format(Locale.getDefault(), "%,.0f XAF", value);
    }

    private double parseAmount(String value) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return 0; }
    }
}
