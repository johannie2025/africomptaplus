package com.wisedesign.africomptaplus.models;
import java.util.List;
public class FinancialReport {
    public double totalRevenue;
    public double grossProfit;
    public double totalExpenses;
    public double netProfit;
    public int    salesCount;
    public double avgBasket;
    public double totalDebts;
    public String periodStart;
    public String periodEnd;
    public List<String[]> topProducts;    // [name, qty, revenue]
    public List<String[]> topCategories;  // [category, revenue]
    public List<String[]> revenueByDay;   // [date, revenue]
}
