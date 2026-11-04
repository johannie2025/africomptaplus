package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.database.Cursor;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.FinancialReport;
import java.util.*;

public class ReportService {

    public static final String PERIOD_DAY       = "day";
    public static final String PERIOD_MONTH     = "month";
    public static final String PERIOD_QUARTER   = "quarter";
    public static final String PERIOD_SEMESTER  = "semester";
    public static final String PERIOD_YEAR      = "year";

    private final DatabaseHelper db;
    public ReportService(Context ctx) { db = DatabaseHelper.getInstance(ctx); }

    // ── Rapport principal ────────────────────────────────────────────────────

    public FinancialReport computeReport(String start, String end) {
        FinancialReport r = new FinancialReport();
        r.periodStart    = start;
        r.periodEnd      = end;
        r.totalRevenue   = queryRevenue(start, end);
        r.grossProfit    = queryGrossProfit(start, end);
        r.totalExpenses  = queryExpenses(start, end);
        r.netProfit      = r.grossProfit - r.totalExpenses;
        r.salesCount     = querySalesCount(start, end);
        r.avgBasket      = r.salesCount > 0 ? r.totalRevenue / r.salesCount : 0;
        r.topProducts    = queryTopProducts(start, end, 5);
        r.topCategories  = queryTopCategories(start, end, 5);
        r.revenueByDay   = queryRevenueByDay(start, end);
        r.totalDebts     = queryOutstandingDebts();
        return r;
    }

    /** Calcule les bornes de dates pour une période nommée. */
    public String[] periodBounds(String period) {
        Calendar cal = Calendar.getInstance();
        String end = fmt(cal.getTime());
        switch (period) {
            case PERIOD_DAY:
                return new String[]{end, end};
            case PERIOD_MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                return new String[]{fmt(cal.getTime()), end};
            case PERIOD_QUARTER:
                int qMonth = (cal.get(Calendar.MONTH) / 3) * 3;
                cal.set(Calendar.MONTH, qMonth);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                return new String[]{fmt(cal.getTime()), end};
            case PERIOD_SEMESTER:
                int sMonth = cal.get(Calendar.MONTH) < 6 ? 0 : 6;
                cal.set(Calendar.MONTH, sMonth);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                return new String[]{fmt(cal.getTime()), end};
            case PERIOD_YEAR:
            default:
                cal.set(Calendar.DAY_OF_YEAR, 1);
                return new String[]{fmt(cal.getTime()), end};
        }
    }

    // ── Requêtes ─────────────────────────────────────────────────────────────

    private double queryRevenue(String s, String e) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_TOTAL_AMOUNT + "),0)"
                + " FROM " + DatabaseHelper.T_SALES
                + " WHERE date(" + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)", new String[]{s, e});
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    private double queryGrossProfit(String s, String e) {
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM((si." + DatabaseHelper.COL_UNIT_PRICE + " - p." + DatabaseHelper.COL_BUYING_PRICE + ") * si." + DatabaseHelper.COL_QUANTITY + "),0)"
                + " FROM " + DatabaseHelper.T_SALE_ITEMS + " si"
                + " INNER JOIN " + DatabaseHelper.T_SALES + " sa ON sa." + DatabaseHelper.COL_ID + "=si." + DatabaseHelper.COL_SALE_ID
                + " INNER JOIN " + DatabaseHelper.T_PRODUCTS + " p ON p." + DatabaseHelper.COL_ID + "=si." + DatabaseHelper.COL_PRODUCT_ID
                + " WHERE date(sa." + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)", new String[]{s, e});
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    private double queryExpenses(String s, String e) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + "),0)"
                + " FROM " + DatabaseHelper.T_EXPENSES
                + " WHERE date(" + DatabaseHelper.COL_DATE + ") BETWEEN date(?) AND date(?)", new String[]{s, e});
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    private int querySalesCount(String s, String e) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_SALES
                + " WHERE date(" + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)", new String[]{s, e});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** Top N produits les plus vendus (en quantité). */
    public List<String[]> queryTopProducts(String s, String e, int limit) {
        List<String[]> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT p." + DatabaseHelper.COL_NAME
                + ", SUM(si." + DatabaseHelper.COL_QUANTITY + ") AS qty"
                + ", SUM(si." + DatabaseHelper.COL_TOTAL_PRICE + ") AS revenue"
                + " FROM " + DatabaseHelper.T_SALE_ITEMS + " si"
                + " INNER JOIN " + DatabaseHelper.T_SALES + " sa ON sa." + DatabaseHelper.COL_ID + "=si." + DatabaseHelper.COL_SALE_ID
                + " INNER JOIN " + DatabaseHelper.T_PRODUCTS + " p ON p." + DatabaseHelper.COL_ID + "=si." + DatabaseHelper.COL_PRODUCT_ID
                + " WHERE date(sa." + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)"
                + " GROUP BY si." + DatabaseHelper.COL_PRODUCT_ID
                + " ORDER BY qty DESC LIMIT ?", new String[]{s, e, String.valueOf(limit)});
        try { while (c.moveToNext()) list.add(new String[]{c.getString(0), c.getString(1), c.getString(2)}); }
        finally { c.close(); }
        return list; // [name, qty, revenue]
    }

    /** Top N catégories par CA. */
    public List<String[]> queryTopCategories(String s, String e, int limit) {
        List<String[]> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT p." + DatabaseHelper.COL_CATEGORY
                + ", SUM(si." + DatabaseHelper.COL_TOTAL_PRICE + ") AS revenue"
                + " FROM " + DatabaseHelper.T_SALE_ITEMS + " si"
                + " INNER JOIN " + DatabaseHelper.T_SALES + " sa ON sa." + DatabaseHelper.COL_ID + "=si." + DatabaseHelper.COL_SALE_ID
                + " INNER JOIN " + DatabaseHelper.T_PRODUCTS + " p ON p." + DatabaseHelper.COL_ID + "=si." + DatabaseHelper.COL_PRODUCT_ID
                + " WHERE date(sa." + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)"
                + " GROUP BY p." + DatabaseHelper.COL_CATEGORY
                + " ORDER BY revenue DESC LIMIT ?", new String[]{s, e, String.valueOf(limit)});
        try { while (c.moveToNext()) list.add(new String[]{c.getString(0), c.getString(1)}); }
        finally { c.close(); }
        return list;
    }

    /** CA par jour sur la période (pour graphique). */
    public List<String[]> queryRevenueByDay(String s, String e) {
        List<String[]> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT date(" + DatabaseHelper.COL_CREATED_AT + ") AS day"
                + ", SUM(" + DatabaseHelper.COL_TOTAL_AMOUNT + ") AS rev"
                + " FROM " + DatabaseHelper.T_SALES
                + " WHERE date(" + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)"
                + " GROUP BY day ORDER BY day ASC", new String[]{s, e});
        try { while (c.moveToNext()) list.add(new String[]{c.getString(0), c.getString(1)}); }
        finally { c.close(); }
        return list;
    }

    private double queryOutstandingDebts() {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + " - " + DatabaseHelper.COL_PAID_AMOUNT + "),0)"
                + " FROM " + DatabaseHelper.T_DEBTS + " WHERE " + DatabaseHelper.COL_STATUS + " != 'paid'", null);
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    // ── Stats dashboard ───────────────────────────────────────────────────────
    public int countSalesToday(String today) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_SALES
                + " WHERE date(" + DatabaseHelper.COL_CREATED_AT + ")=date(?)", new String[]{today});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }
    public int countLowStockProducts() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_PRODUCTS
                + " WHERE " + DatabaseHelper.COL_STOCK + "<=" + DatabaseHelper.COL_MIN_STOCK, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }
    public double revenueToday(String today) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_TOTAL_AMOUNT + "),0) FROM " + DatabaseHelper.T_SALES
                + " WHERE date(" + DatabaseHelper.COL_CREATED_AT + ")=date(?)", new String[]{today});
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }
    public double outstandingDebts() { return queryOutstandingDebts(); }

    private String fmt(java.util.Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }
}
