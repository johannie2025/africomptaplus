package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.tabs.TabLayout;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.FinancialReport;
import com.wisedesign.africomptaplus.services.ReportService;
import com.wisedesign.africomptaplus.services.ReportPdfManager;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ReportActivity V2 — 5 périodes : Jour / Mois / Trimestre / Semestre / Année
 * + Top produits + Top catégories + Panier moyen + Dettes en cours
 */
public class ReportActivity extends AppCompatActivity {

    private ReportService rs;
    private ReportPdfManager reportPdfManager;
    private TextView tvRevenue, tvGrossProfit, tvExpenses, tvNetProfit,
                     tvPeriod, tvSalesCount, tvAvgBasket, tvDebts,
                     tvTopProducts, tvTopCategories;
    private String currentPeriod = ReportService.PERIOD_DAY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        rs = new ReportService(this);
        reportPdfManager = new ReportPdfManager(this);

        tvRevenue       = findViewById(R.id.tvRevenue);
        tvGrossProfit   = findViewById(R.id.tvGrossProfit);
        tvExpenses      = findViewById(R.id.tvExpenses);
        tvNetProfit     = findViewById(R.id.tvNetProfit);
        tvPeriod        = findViewById(R.id.tvPeriod);
        tvSalesCount    = findViewById(R.id.tvSalesCount);
        tvAvgBasket     = findViewById(R.id.tvAvgBasket);
        tvDebts         = findViewById(R.id.tvDebts);
        tvTopProducts   = findViewById(R.id.tvTopProducts);
        tvTopCategories = findViewById(R.id.tvTopCategories);
        findViewById(R.id.btnExportReport).setOnClickListener(v -> exportReport());

        TabLayout tabs = findViewById(R.id.tabPeriod);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: currentPeriod = ReportService.PERIOD_DAY;      break;
                    case 1: currentPeriod = ReportService.PERIOD_MONTH;    break;
                    case 2: currentPeriod = ReportService.PERIOD_QUARTER;  break;
                    case 3: currentPeriod = ReportService.PERIOD_SEMESTER; break;
                    case 4: currentPeriod = ReportService.PERIOD_YEAR;     break;
                }
                loadReport();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadReport();
    }

    private void loadReport() {
        String[] bounds = rs.periodBounds(currentPeriod);
        String   start  = bounds[0], end = bounds[1];

        SimpleDateFormat sdfFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        try {
            Date ds = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(start);
            Date de = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(end);
            tvPeriod.setText(getString(R.string.report_period_range, sdfFmt.format(ds), sdfFmt.format(de)));
        } catch (Exception ignored) { tvPeriod.setText(start + " → " + end); }

        FinancialReport r = rs.computeReport(start, end);

        tvRevenue.setText(fmt(r.totalRevenue) + " XAF");
        tvGrossProfit.setText(fmt(r.grossProfit) + " XAF");
        tvExpenses.setText(fmt(r.totalExpenses) + " XAF");
        tvNetProfit.setText(fmt(r.netProfit) + " XAF");
        tvNetProfit.setTextColor(r.netProfit >= 0 ? 0xFF43A047 : 0xFFE53935);
        tvSalesCount.setText(getString(R.string.report_sales_count_value, r.salesCount));
        tvAvgBasket.setText(fmt(r.avgBasket) + " XAF");
        tvDebts.setText(fmt(r.totalDebts) + " XAF");

        // Top produits
        StringBuilder sbP = new StringBuilder();
        if (r.topProducts != null && !r.topProducts.isEmpty()) {
            for (int i = 0; i < r.topProducts.size(); i++) {
                String[] p = r.topProducts.get(i);
                sbP.append(i + 1).append(". ").append(p[0])
                   .append("  ×").append(p[1])
                   .append("  →  ").append(fmt(Double.parseDouble(p[2]))).append(" XAF\n");
            }
        } else { sbP.append(getString(R.string.report_no_sales)); }
        tvTopProducts.setText(sbP.toString().trim());

        // Top catégories
        StringBuilder sbC = new StringBuilder();
        if (r.topCategories != null && !r.topCategories.isEmpty()) {
            for (int i = 0; i < r.topCategories.size(); i++) {
                String[] c = r.topCategories.get(i);
                sbC.append(i + 1).append(". ").append(localizeCategory(c[0]))
                   .append("  →  ").append(fmt(Double.parseDouble(c[1]))).append(" XAF\n");
            }
        } else { sbC.append("—"); }
        tvTopCategories.setText(sbC.toString().trim());
    }

    private String fmt(double v) { return String.format(Locale.getDefault(), "%,.0f", v); }

    private String localizeCategory(String value) {
        String[] stored = {"Loyer", "Personnel", "Transport", "Stock", "Électricité",
                "Eau", "Téléphone/Internet", "Publicité", "Fournitures", "Autre"};
        String[] translated = getResources().getStringArray(R.array.expense_categories);
        for (int i = 0; i < stored.length; i++) if (stored[i].equals(value)) return translated[i];
        return value;
    }

    private void exportReport() {
        String[] bounds = rs.periodBounds(currentPeriod);
        FinancialReport report = rs.computeReport(bounds[0], bounds[1]);
        Uri pdfUri = reportPdfManager.generate(report, bounds[0], bounds[1]);
        if (pdfUri == null) {
            Toast.makeText(this, R.string.report_pdf_error, Toast.LENGTH_LONG).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, pdfUri);
        share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_pdf_title, bounds[0], bounds[1]));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.report_share_title)));
    }
}
