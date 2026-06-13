package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.services.ReportService;
import com.wisedesign.africomptaplus.services.SecurityManager;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private ReportService   reportService;
    private SecurityManager securityManager;
    private DatabaseHelper  db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        reportService   = new ReportService(this);
        securityManager = new SecurityManager(this);
        db              = DatabaseHelper.getInstance(this);

        // Bouton paramètres
        TextView tvSettings = findViewById(R.id.tvSettingsBtn);
        if (tvSettings != null)
            tvSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        setupCards();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    private void refreshDashboard() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        double revenue   = reportService.revenueToday(today);
        int    sales     = reportService.countSalesToday(today);
        int    lowStock  = reportService.countLowStockProducts();
        double debts     = reportService.outstandingDebts();
        int    daysLeft  = securityManager.getRemainingDays();
        String shopName  = db.getConfig("shop_name");

        setText(R.id.tvShopName,    shopName.isEmpty() ? "AfriCompta+" : shopName);
        setText(R.id.tvRevenueToday, fmt(revenue) + " XAF");
        setText(R.id.tvSalesCount,   String.valueOf(sales));
        setText(R.id.tvLowStock,     String.valueOf(lowStock));
        setText(R.id.tvDebts,        fmt(debts) + " XAF");

        if (!securityManager.isActivated()) {
            setText(R.id.tvTrialBadge, "⏳ Essai : " + daysLeft + "j restant(s)");
        } else {
            setText(R.id.tvTrialBadge, "✅ Licence active");
        }
    }

    private void setupCards() {
        clickCard(R.id.cardSale,     SaleActivity.class);
        clickCard(R.id.cardProducts, ProductListActivity.class);
        clickCard(R.id.cardClients,  ClientListActivity.class);
        clickCard(R.id.cardDebts,    DebtActivity.class);
        clickCard(R.id.cardExpenses, ExpenseActivity.class);
        clickCard(R.id.cardReports,  ReportActivity.class);
        clickCard(R.id.cardHistory,  SaleHistoryActivity.class);
    }

    private void clickCard(int id, Class<?> target) {
        CardView c = findViewById(id);
        if (c != null) c.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }

    private void setText(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private String fmt(double v) { return String.format(Locale.FRENCH, "%,.0f", v); }
}
