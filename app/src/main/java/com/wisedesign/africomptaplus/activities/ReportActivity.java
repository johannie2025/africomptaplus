package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.FinancialReport;
import com.wisedesign.africomptaplus.services.ReportService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private ReportService reportService;
    private TextView tvRevenue, tvGrossProfit, tvExpenses, tvNetProfit, tvPeriod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        reportService = new ReportService(this);

        tvRevenue     = findViewById(R.id.tvRevenue);
        tvGrossProfit = findViewById(R.id.tvGrossProfit);
        tvExpenses    = findViewById(R.id.tvExpenses);
        tvNetProfit   = findViewById(R.id.tvNetProfit);
        tvPeriod      = findViewById(R.id.tvPeriod);

        TabLayout tabLayout = findViewById(R.id.tabPeriod);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { loadReport(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadReport(0);
    }

    private void loadReport(int period) {
        SimpleDateFormat sdf    = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdfFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        Date today   = cal.getTime();

        String start, end;

        switch (period) {
            case 1: // Ce mois
                cal.set(Calendar.DAY_OF_MONTH, 1);
                start = sdf.format(cal.getTime());
                end   = sdf.format(today);
                tvPeriod.setText(getString(R.string.period_range_from_to, sdfFmt.format(cal.getTime()), sdfFmt.format(today)));
                break;
            case 2: // Cette année
                cal.set(Calendar.DAY_OF_YEAR, 1);
                start = sdf.format(cal.getTime());
                end   = sdf.format(today);
                String currentYear = new SimpleDateFormat("yyyy", Locale.US).format(today);
                tvPeriod.setText(getString(R.string.period_range_from_to, "01/01/" + currentYear, sdfFmt.format(today)));
                break;
            default: // Aujourd'hui
                start = sdf.format(today);
                end   = sdf.format(today);
                tvPeriod.setText(getString(R.string.period_today, sdfFmt.format(today)));
        }

        FinancialReport report = reportService.computeReport(start, end);
        displayReport(report);
    }

    private void displayReport(FinancialReport r) {
        tvRevenue.setText(formatAmount(r.totalRevenue)  + " XAF");
        tvGrossProfit.setText(formatAmount(r.grossProfit) + " XAF");
        tvExpenses.setText(formatAmount(r.totalExpenses) + " XAF");

        tvNetProfit.setText(formatAmount(r.netProfit) + " XAF");
        tvNetProfit.setTextColor(r.netProfit >= 0 ? 0xFF43A047 : 0xFFE53935);
    }

    private String formatAmount(double amount) {
        return String.format(Locale.getDefault(), "%,.0f", amount);
    }
}