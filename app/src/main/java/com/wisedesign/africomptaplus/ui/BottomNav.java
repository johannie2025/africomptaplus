package com.wisedesign.africomptaplus.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.activities.*;

/** Barre de navigation commune : Vente · Produits · [+] · Clients · Dettes · Rapports. */
public final class BottomNav {
    public static final int NONE = 0, SALE = 1, PRODUCTS = 2, CLIENTS = 3, DEBTS = 4, REPORTS = 5;

    private BottomNav() {}

    public static void attach(Activity a, int selected) {
        bind(a, R.id.navSale,     R.id.navSaleIcon,     R.id.navSaleLabel,     SaleActivity.class,        SALE == selected);
        bind(a, R.id.navProducts, R.id.navProductsIcon, R.id.navProductsLabel, ProductListActivity.class, PRODUCTS == selected);
        bind(a, R.id.navClients,  R.id.navClientsIcon,  R.id.navClientsLabel,  ClientListActivity.class,  CLIENTS == selected);
        bind(a, R.id.navDebts,    R.id.navDebtsIcon,    R.id.navDebtsLabel,    DebtActivity.class,        DEBTS == selected);
        bind(a, R.id.navReports,  R.id.navReportsIcon,  R.id.navReportsLabel,  ReportActivity.class,      REPORTS == selected);

        View fab = a.findViewById(R.id.navFab);
        if (fab != null) {
            fab.setOnClickListener(v -> showQuickActions(a, selected));
            UiKit.pressScale(fab);
        }
    }

    private static void bind(Activity a, int boxId, int iconId, int labelId, Class<?> target, boolean selected) {
        View box = a.findViewById(boxId);
        if (box == null) return;
        ImageView icon = a.findViewById(iconId);
        TextView label = a.findViewById(labelId);
        if (icon != null) icon.setSelected(selected);
        if (label != null) label.setSelected(selected);
        if (selected) box.setBackgroundResource(R.drawable.bg_nav_selected);
        box.setOnClickListener(v -> { if (!selected) go(a, target, null); });
    }

    /** Ouvre une destination ; remplace l'écran courant (sauf l'accueil) pour garder une pile courte. */
    public static void go(Activity a, Class<?> target, String extra) {
        Intent i = new Intent(a, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (extra != null) i.putExtra(extra, true);
        a.startActivity(i);
        if (!(a instanceof MainActivity)) a.finish();
        a.overridePendingTransition(0, 0);
    }

    private static void showQuickActions(Activity a, int selected) {
        BottomSheetDialog dlg = new BottomSheetDialog(a);
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_quick_actions, null);
        dlg.setContentView(v);

        click(v, R.id.qaSale, dlg, () -> { if (selected != SALE) go(a, SaleActivity.class, null); });
        click(v, R.id.qaProduct, dlg, () -> a.startActivity(new Intent(a, ProductFormActivity.class)));
        click(v, R.id.qaExpense, dlg, () -> {
            Intent i = new Intent(a, ExpenseActivity.class);
            i.putExtra(ExpenseActivity.EXTRA_OPEN_ADD, true);
            a.startActivity(i);
            if (!(a instanceof MainActivity) && !(a instanceof ExpenseActivity)) a.finish();
        });
        click(v, R.id.qaHome, dlg, () -> {
            if (a instanceof MainActivity) return;
            Intent i = new Intent(a, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(i);
        });
        click(v, R.id.qaHistory, dlg, () -> a.startActivity(new Intent(a, SaleHistoryActivity.class)));
        click(v, R.id.qaSettings, dlg, () -> a.startActivity(new Intent(a, SettingsActivity.class)));
        dlg.show();
    }

    private static void click(View root, int id, BottomSheetDialog dlg, Runnable r) {
        View b = root.findViewById(id);
        UiKit.pressScale(b);
        b.setOnClickListener(x -> { dlg.dismiss(); r.run(); });
    }
}
