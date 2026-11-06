package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.services.ReportService;
import com.wisedesign.africomptaplus.services.SecurityManager;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;
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

        View settings = findViewById(R.id.btnSettings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        setupCards();
        BottomNav.attach(this, BottomNav.NONE);
        securityManager.recheckOnlineIfDue(); // re-validation licence (max 1×/24h, silencieuse)
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
        View ca = findViewById(R.id.cardAcct);
        if (ca != null) {
            String mode = com.wisedesign.africomptaplus.db.DatabaseHelper.getInstance(this).getConfig("acct_mode");
            ca.setVisibility(mode == null || mode.isEmpty() || "off".equals(mode) ? View.GONE : View.VISIBLE);
        }
    }

    private void refreshDashboard() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        double revenue   = reportService.revenueToday(today);
        int    sales     = reportService.countSalesToday(today);
        int    lowStock  = reportService.countLowStockProducts();
        double debts     = reportService.outstandingDebts();
        int    daysLeft  = securityManager.getRemainingDays();
        String shopName  = db.getConfig("shop_name");

        setText(R.id.tvShopName,     shopName.isEmpty() ? "AfriCompta+" : shopName);
        setText(R.id.tvRevenueToday, UiKit.xaf(revenue));
        setText(R.id.tvSalesCount,   String.valueOf(sales));
        setText(R.id.tvLowStock,     String.valueOf(lowStock));
        setText(R.id.tvDebts,        UiKit.xaf(debts));

        if (!securityManager.isActivated()) {
            setText(R.id.tvTrialBadge, "Essai : " + daysLeft + " j restant(s)");
        } else {
            setText(R.id.tvTrialBadge, "Licence active");
        }

        // Logo boutique dans l'en-tête
        ImageView iv = findViewById(R.id.ivShopLogo);
        Bitmap logo = UiKit.loadLogo(this, 160);
        if (logo != null) { iv.setBackgroundResource(R.drawable.bg_logo_round); iv.setPadding(8, 8, 8, 8); iv.setImageBitmap(logo); }
    }

    private void setupCards() {
        click(R.id.cardSale,     SaleActivity.class);
        click(R.id.cardProducts, ProductListActivity.class);
        click(R.id.cardClients,  ClientListActivity.class);
        click(R.id.cardDebts,    DebtActivity.class);
        click(R.id.cardExpenses, ExpenseActivity.class);
        click(R.id.cardReports,  ReportActivity.class);
        click(R.id.cardHistory,  SaleHistoryActivity.class);
        click(R.id.cardDocs,     DocumentListActivity.class);
        click(R.id.cardAcct,     AccountingActivity.class);
        click(R.id.cardSettings, SettingsActivity.class);
    }

    private void click(int id, Class<?> target) {
        View c = findViewById(id);
        if (c == null) return;
        c.setOnClickListener(v -> startActivity(new Intent(this, target)));
        UiKit.pressScale(c);
    }

    private void setText(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }
}
