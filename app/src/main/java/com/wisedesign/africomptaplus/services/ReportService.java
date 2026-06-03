package com.wisedesign.africomptaplus.services;

import android.content.Context;
import android.database.Cursor;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.FinancialReport;

/**
 * ReportService — Moteur de rapports financiers.
 *
 * Calcule sur une plage de dates :
 *   totalRevenue  = ΣSales.total_amount
 *   grossProfit   = Σ((unit_price - buying_price) * quantity) sur les items vendus
 *   totalExpenses = ΣExpenses.amount
 *   netProfit     = grossProfit − totalExpenses
 */
public class ReportService {

    private final DatabaseHelper db;

    public ReportService(Context context) {
        this.db = DatabaseHelper.getInstance(context);
    }

    /**
     * Calcule le rapport financier pour la période [dateStart ; dateEnd].
     * Les dates sont au format ISO-8601 : "yyyy-MM-dd" ou "yyyy-MM-dd HH:mm:ss".
     *
     * @param dateStart début de période (inclusif)
     * @param dateEnd   fin de période (inclusif)
     * @return FinancialReport rempli
     */
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

    // ── Revenue ───────────────────────────────────────────────────────────────

    /**
     * Somme de toutes les ventes (total_amount) dans la période.
     */
    private double queryTotalRevenue(String start, String end) {
        String sql = "SELECT COALESCE(SUM(s." + DatabaseHelper.COL_TOTAL_AMOUNT + "), 0)"
                + " FROM " + DatabaseHelper.T_SALES + " s"
                + " WHERE s." + DatabaseHelper.COL_CREATED_AT + " BETWEEN ? AND ?";

        Cursor cursor = db.rawQuery(sql, new String[]{start, end});
        try {
            if (cursor.moveToFirst()) return cursor.getDouble(0);
            return 0;
        } finally {
            cursor.close();
        }
    }

    // ── Gross Profit ──────────────────────────────────────────────────────────

    /**
     * Bénéfice brut = Σ((prix_vente − prix_achat) × quantité) pour tous les articles vendus
     * dans la période.
     *
     * Jointure : sale_items → sales (filtre date) → products (récupère buying_price).
     */
    private double queryGrossProfit(String start, String end) {
        String sql =
                "SELECT COALESCE(SUM((si." + DatabaseHelper.COL_UNIT_PRICE
                        + " - p." + DatabaseHelper.COL_BUYING_PRICE
                        + ") * si." + DatabaseHelper.COL_QUANTITY + "), 0)"
                + " FROM " + DatabaseHelper.T_SALE_ITEMS + " si"
                + " INNER JOIN " + DatabaseHelper.T_SALES    + " s ON s." + DatabaseHelper.COL_ID + " = si." + DatabaseHelper.COL_SALE_ID
                + " INNER JOIN " + DatabaseHelper.T_PRODUCTS + " p ON p." + DatabaseHelper.COL_ID + " = si." + DatabaseHelper.COL_PRODUCT_ID
                + " WHERE s." + DatabaseHelper.COL_CREATED_AT + " BETWEEN ? AND ?";

        Cursor cursor = db.rawQuery(sql, new String[]{start, end});
        try {
            if (cursor.moveToFirst()) return cursor.getDouble(0);
            return 0;
        } finally {
            cursor.close();
        }
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    /**
     * Somme de toutes les dépenses dans la période.
     */
    private double queryTotalExpenses(String start, String end) {
        String sql = "SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + "), 0)"
                + " FROM " + DatabaseHelper.T_EXPENSES
                + " WHERE " + DatabaseHelper.COL_DATE + " BETWEEN ? AND ?";

        Cursor cursor = db.rawQuery(sql, new String[]{start, end});
        try {
            if (cursor.moveToFirst()) return cursor.getDouble(0);
            return 0;
        } finally {
            cursor.close();
        }
    }

    // ── Stats rapides pour dashboard ──────────────────────────────────────────

    /** Nombre de ventes aujourd'hui. */
    public int countSalesToday(String today) {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.T_SALES
                + " WHERE " + DatabaseHelper.COL_CREATED_AT + " LIKE ?",
                new String[]{today + "%"});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally { c.close(); }
    }

    /** Nombre de produits avec stock <= min_stock. */
    public int countLowStockProducts() {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.T_PRODUCTS
                + " WHERE " + DatabaseHelper.COL_STOCK + " <= " + DatabaseHelper.COL_MIN_STOCK, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally { c.close(); }
    }

    /** Chiffre d'affaires du jour. */
    public double revenueToday(String today) {
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(" + DatabaseHelper.COL_TOTAL_AMOUNT + "), 0)"
                + " FROM " + DatabaseHelper.T_SALES
                + " WHERE " + DatabaseHelper.COL_CREATED_AT + " LIKE ?",
                new String[]{today + "%"});
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally { c.close(); }
    }
}
