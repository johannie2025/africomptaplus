package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.services.ReportService;
import com.wisedesign.africomptaplus.services.SecurityManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MainActivity — Dashboard principal.
 * Affiche les KPIs du jour et les raccourcis vers les modules.
 */
public class MainActivity extends AppCompatActivity {

    private ReportService reportService;
    private SecurityManager securityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        reportService   = new ReportService(this);
        securityManager = new SecurityManager(this);

        setupCards();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    private void refreshDashboard() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        // KPIs du jour
        double revenueToday  = reportService.revenueToday(today);
        int    salesCount    = reportService.countSalesToday(today);
        int    lowStock      = reportService.countLowStockProducts();
        int    daysRemaining = securityManager.getRemainingDays();

        setText(R.id.tvRevenueToday,  formatAmount(revenueToday) + " XAF");
        setText(R.id.tvSalesCount,    salesCount + " vente(s)");
        setText(R.id.tvLowStock,      lowStock + " produit(s)");
        setText(R.id.tvTrialBadge,    "Essai : " + daysRemaining + "j");
    }

    private void setupCards() {
        // Navigation vers les modules
        clickCard(R.id.cardSale,      SaleActivity.class);
        clickCard(R.id.cardProducts,  ProductListActivity.class);
        clickCard(R.id.cardExpenses,  ExpenseActivity.class);
        clickCard(R.id.cardReports,   ReportActivity.class);
        clickCard(R.id.cardHistory,   SaleHistoryActivity.class);
    }

    private void clickCard(int cardId, Class<?> target) {
        CardView card = findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, target)));
        }
    }

    private void setText(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private String formatAmount(double amount) {
        return String.format(Locale.FRENCH, "%,.0f", amount);
    }
}
