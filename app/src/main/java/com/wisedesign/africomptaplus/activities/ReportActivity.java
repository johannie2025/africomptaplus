package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.widget.Button;
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

        // Rapport du jour par défaut
        loadReport(0);
    }

    /**
     * 0 = Aujourd'hui, 1 = Ce mois, 2 = Cette année
     */
    private void loadReport(int period) {
        SimpleDateFormat sdf    = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdfFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH);
        Calendar cal = Calendar.getInstance();
        Date today   = cal.getTime();

        String start, end;

        switch (period) {
            case 1: // Ce mois
                cal.set(Calendar.DAY_OF_MONTH, 1);
                start = sdf.format(cal.getTime());
                end   = sdf.format(today);
                tvPeriod.setText("Du " + sdfFmt.format(cal.getTime()) + " au " + sdfFmt.format(today));
                break;
            case 2: // Cette année
                cal.set(Calendar.DAY_OF_YEAR, 1);
                start = sdf.format(cal.getTime());
                end   = sdf.format(today);
                tvPeriod.setText("Du 01/01/" + new SimpleDateFormat("yyyy", Locale.US).format(today) + " au " + sdfFmt.format(today));
                break;
            default: // Aujourd'hui
                start = sdf.format(today);
                end   = sdf.format(today);
                tvPeriod.setText("Aujourd'hui : " + sdfFmt.format(today));
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
        return String.format(Locale.FRENCH, "%,.0f", amount);
    }
}
