package com.wisedesign.africomptaplus.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Histogramme léger du CA (jours ≤ 31 sinon regroupé par mois). Aucune dépendance. */
public class BarChartView extends View {
    private final Paint bar   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barHi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> labels = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();
    private final float dp;

    public BarChartView(Context c) { this(c, null); }
    public BarChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        bar.setColor(0xFFBFD0F5);
        barHi.setColor(0xFF1E40AF);
        grid.setColor(0xFFE2E8F0); grid.setStrokeWidth(dp);
        txt.setColor(0xFF64748B); txt.setTextSize(10 * dp);
    }

    /** @param points liste de [yyyy-MM-dd, montant] */
    public void setData(List<String[]> points) {
        labels.clear(); values.clear();
        if (points != null && points.size() > 31) {
            Map<String, Double> m = new LinkedHashMap<>();
            for (String[] p : points) {
                String k = p[0].substring(0, 7);
                Double cur = m.get(k);
                m.put(k, (cur == null ? 0 : cur) + parse(p[1]));
            }
            for (Map.Entry<String, Double> e : m.entrySet()) {
                labels.add(monthLabel(e.getKey().substring(5)));
                values.add(e.getValue());
            }
        } else if (points != null) {
            for (String[] p : points) {
                labels.add(p[0].length() >= 10 ? p[0].substring(8, 10) : p[0]);
                values.add(parse(p[1]));
            }
        }
        invalidate();
    }

    private static double parse(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private static String monthLabel(String mm) {
        String[] n = {"Jan","Fév","Mar","Avr","Mai","Juin","Juil","Août","Sep","Oct","Nov","Déc"};
        try { return n[Integer.parseInt(mm) - 1]; } catch (Exception e) { return mm; }
    }
    private static String compact(double v) {
        if (v >= 1_000_000) return String.format(java.util.Locale.FRENCH, "%.1fM", v / 1_000_000);
        if (v >= 1_000) return Math.round(v / 1_000) + "k";
        return String.valueOf(Math.round(v));
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        float w = getWidth(), h = getHeight();
        if (values.isEmpty()) {
            txt.setTextAlign(Paint.Align.CENTER);
            cv.drawText("Aucune vente sur la période", w / 2f, h / 2f, txt);
            return;
        }
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;

        float left = 34 * dp, bottom = h - 18 * dp, top = 6 * dp;
        txt.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 2; i++) {
            float y = bottom - (bottom - top) * i / 2f;
            cv.drawLine(left, y, w, y, grid);
            cv.drawText(compact(max * i / 2f), left - 4 * dp, y + 3 * dp, txt);
        }

        int n = values.size();
        float slot = (w - left) / n;
        float bw = Math.min(slot * 0.62f, 28 * dp);
        int step = Math.max(1, (int) Math.ceil(n / 8.0));
        int peak = values.indexOf(java.util.Collections.max(values));
        txt.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float cx = left + slot * i + slot / 2f;
            float bh = (float) ((bottom - top) * (values.get(i) / max));
            RectF r = new RectF(cx - bw / 2f, bottom - Math.max(bh, 2 * dp), cx + bw / 2f, bottom);
            cv.drawRoundRect(r, 4 * dp, 4 * dp, i == peak ? barHi : bar);
            if (i % step == 0) cv.drawText(labels.get(i), cx, h - 4 * dp, txt);
        }
    }
}
