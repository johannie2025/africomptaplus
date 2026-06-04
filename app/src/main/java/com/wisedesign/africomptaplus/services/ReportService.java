package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.database.Cursor;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.FinancialReport;

public class ReportService {

    private final DatabaseHelper db;

    public ReportService(Context context) {
        this.db = DatabaseHelper.getInstance(context);
    }

    public FinancialReport computeReport(String dateStart, String dateEnd) {
        FinancialReport report = new FinancialReport();
        report.periodStart = dateStart;
        report.periodEnd   = dateEnd;

        report.totalRevenue  = queryTotalRevenue(dateStart, dateEnd);
        report.grossProfit   = queryGrossProfit(dateStart, dateEnd);
        report.totalExpenses = queryTotalExpenses(dateStart, dateEnd);
        report.netProfit     = report.grossProfit - report.totalExpenses;

        return report;
    }

    // ── Revenue (CA) ──────────────────────────────────────────────────────────

    private double queryTotalRevenue(String start, String end) {
        // CORRECTION : Normalisation des dates textuelles avec date() pour inclure les fins de journées
        String sql = "SELECT COALESCE(SUM(s." + DatabaseHelper.COL_TOTAL_AMOUNT + "), 0)"
                + " FROM " + DatabaseHelper.T_SALES + " s"
                + " WHERE date(s." + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)";

        Cursor cursor = db.rawQuery(sql, new String[]{start, end});
        try {
            if (cursor.moveToFirst()) return cursor.getDouble(0);
            return 0;
        } finally {
            cursor.close();
        }
    }

    // ── Gross Profit (Bénéfice Brut) ──────────────────────────────────────────

    private double queryGrossProfit(String start, String end) {
        // CORRECTION : Normalisation date() appliquée aussi ici
        String sql =
                "SELECT COALESCE(SUM((si." + DatabaseHelper.COL_UNIT_PRICE
                        + " - p." + DatabaseHelper.COL_BUYING_PRICE
                        + ") * si." + DatabaseHelper.COL_QUANTITY + "), 0)"
                + " FROM " + DatabaseHelper.T_SALE_ITEMS + " si"
                + " INNER JOIN " + DatabaseHelper.T_SALES    + " s ON s." + DatabaseHelper.COL_ID + " = si." + DatabaseHelper.COL_SALE_ID
                + " INNER JOIN " + DatabaseHelper.T_PRODUCTS + " p ON p." + DatabaseHelper.COL_ID + " = si." + DatabaseHelper.COL_PRODUCT_ID
                + " WHERE date(s." + DatabaseHelper.COL_CREATED_AT + ") BETWEEN date(?) AND date(?)";

        Cursor cursor = db.rawQuery(sql, new String[]{start, end});
        try {
            if (cursor.moveToFirst()) return cursor.getDouble(0);
            return 0;
        } finally {
            cursor.close();
        }
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    private double queryTotalExpenses(String start, String end) {
        String sql = "SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + "), 0)"
                + " FROM " + DatabaseHelper.T_EXPENSES
                + " WHERE date(" + DatabaseHelper.COL_DATE + ") BETWEEN date(?) AND date(?)";

        Cursor cursor = db.rawQuery(sql, new String[]{start, end});
        try {
            if (cursor.moveToFirst()) return cursor.getDouble(0);
            return 0;
        } finally {
            cursor.close();
        }
    }

    // ── Stats rapides pour dashboard ──────────────────────────────────────────

    public int countSalesToday(String today) {
        // CORRECTION : Utilisation de date() au lieu de LIKE pour éviter les faux négatifs
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.T_SALES
                + " WHERE date(" + DatabaseHelper.COL_CREATED_AT + ") = date(?)",
                new String[]{today});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally { c.close(); }
    }

    public int countLowStockProducts() {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.T_PRODUCTS
                + " WHERE " + DatabaseHelper.COL_STOCK + " <= " + DatabaseHelper.COL_MIN_STOCK, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally { c.close(); }
    }

    public double revenueToday(String today) {
        // CORRECTION : Utilisation de date()
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(" + DatabaseHelper.COL_TOTAL_AMOUNT + "), 0)"
                + " FROM " + DatabaseHelper.T_SALES
                + " WHERE date(" + DatabaseHelper.COL_CREATED_AT + ") = date(?)",
                new String[]{today});
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally { c.close(); }
    }
}