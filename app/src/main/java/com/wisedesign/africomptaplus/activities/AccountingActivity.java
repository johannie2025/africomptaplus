package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.services.AccountingService;
import com.wisedesign.africomptaplus.services.ReportTable;
import com.wisedesign.africomptaplus.services.TablePdf;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.FilterBar;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.*;

/** Comptabilité simple (livre de caisse, synthèse) ou double SYSCOHADA (journal, grand livre, balance, résultat, bilan, analytique). */
public class AccountingActivity extends AppCompatActivity {
    private AccountingService svc;
    private String mode, q = "", from = "", to = "";
    private String[] tabs;
    private int tab = 0;
    private ReportTable current;
    private final List<TextView> tabViews = new ArrayList<>();
    private TableAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_accounting);
        UiKit.topBar(this, "Comptabilité");
        BottomNav.attach(this, BottomNav.NONE);
        svc = new AccountingService(this);
        mode = svc.mode();
        if ("off".equals(mode)) {
            Toast.makeText(this, "Activez la comptabilité dans Réglages", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        tabs = "double".equals(mode)
                ? new String[]{"Journal", "Grand livre", "Balance", "Résultat", "Bilan", "Analytique", "Plan", "Saisies"}
                : new String[]{"Livre de caisse", "Synthèse", "Saisies"};

        RecyclerView rv = findViewById(R.id.rvTable);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TableAdapter();
        rv.setAdapter(adapter);

        LinearLayout ll = findViewById(R.id.llTabs);
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(tabs[i]);
            t.setTextSize(13);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            int h = dp(14), v = dp(8);
            t.setPadding(h, v, h, v);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            t.setLayoutParams(lp);
            t.setOnClickListener(x -> { tab = idx; reload(); });
            ll.addView(t); tabViews.add(t);
        }
        FilterBar.attach(this, (qq, f, t) -> { q = qq; from = f; to = t; reload(); });
        findViewById(R.id.btnAddEntry).setOnClickListener(v -> {
            if ("Plan".equals(tabs[tab])) addAccountDialog(); else entryDialog();
        });
        findViewById(R.id.btnExportTable).setOnClickListener(v -> export());
        UiKit.pressScale(findViewById(R.id.btnAddEntry), findViewById(R.id.btnExportTable));
        reload();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private ReportTable build() {
        switch (tabs[tab]) {
            case "Journal":        return svc.journal(from, to, q);
            case "Grand livre":    return svc.ledger(from, to, q);
            case "Balance":        return svc.balance(from, to, q);
            case "Résultat":       return svc.income(from, to);
            case "Bilan":          return svc.balanceSheet(to);
            case "Analytique":     return svc.analytic(from, to);
            case "Plan":           return svc.plan(q);
            case "Saisies":        return svc.manualList(from, to, q);
            case "Synthèse":       return svc.simpleSummary(from, to);
            default:               return svc.cashBook(from, to, q);
        }
    }

    private String subtitle() {
        String p = (from.isEmpty() && to.isEmpty()) ? "Toutes périodes"
                : "Du " + (from.isEmpty() ? "début" : from) + " au " + (to.isEmpty() ? "ce jour" : to);
        if ("Bilan".equals(tabs[tab])) p = "Situation cumulée " + (to.isEmpty() ? "à ce jour" : "au " + to);
        return p + " · " + ("double".equals(mode) ? "Comptabilité générale & analytique (SYSCOHADA)" : "Comptabilité simple");
    }

    private void reload() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = tabViews.get(i);
            boolean sel = i == tab;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(18));
            g.setColor(sel ? 0xFF1E40AF : 0xFFFFFFFF);
            g.setStroke(dp(1), sel ? 0xFF1E40AF : 0xFFE2E8F0);
            t.setBackground(g);
            t.setTextColor(sel ? Color.WHITE : 0xFF64748B);
        }
        ((TextView) findViewById(R.id.btnAddEntry)).setText("Plan".equals(tabs[tab]) ? "+ Compte" : "+ Écriture");
        current = build();
        ((TextView) findViewById(R.id.tvInfo)).setText(subtitle());

        LinearLayout hd = findViewById(R.id.llHeader);
        hd.removeAllViews();
        for (int c = 0; c < current.headers.length; c++) hd.addView(cell(current.headers[c], current.weights[c], current.right[c], true, Color.WHITE));
        adapter.notifyDataSetChanged();
        findViewById(R.id.tvEmpty).setVisibility(current.rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private TextView cell(String s, float w, boolean right, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w));
        t.setText(s);
        t.setTextSize(10);
        t.setTextColor(color);
        t.setPadding(dp(4), 0, dp(4), 0);
        t.setGravity(right ? Gravity.END : Gravity.START);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void export() {
        if (current == null) return;
        Uri u = TablePdf.export(this, current, subtitle(), "compta_" + tabs[tab].replace(' ', '_') + "_" + System.currentTimeMillis());
        if (u == null) { Toast.makeText(this, "Erreur de génération du PDF", Toast.LENGTH_SHORT).show(); return; }
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("application/pdf");
        s.putExtra(Intent.EXTRA_STREAM, u);
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s, "Partager l'état"));
    }

    // ── Saisie d'écriture ───────────────────────────────────────────────────
    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setInputType(type);
        return e;
    }

    private void entryDialog() {
        final boolean dbl = "double".equals(mode);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        // modèles (double) / type (simple)
        final String[] tplNames, tplJ, tplD, tplC;
        if (dbl) {
            tplNames = new String[]{"Écriture libre", "Apport en capital", "Achat de marchandises comptant", "Achat de marchandises à crédit",
                    "Règlement fournisseur", "Dépôt en banque", "Retrait personnel (exploitant)", "Emprunt reçu", "Paiement de loyer"};
            tplJ = new String[]{"OD", "CA", "AC", "AC", "CA", "BQ", "CA", "BQ", "AC"};
            tplD = new String[]{"", "571", "311", "311", "401", "521", "108", "521", "6222"};
            tplC = new String[]{"", "101", "571", "401", "571", "571", "571", "162", "571"};
        } else {
            tplNames = new String[]{"Entrée de caisse", "Sortie de caisse"};
            tplJ = new String[]{"CA", "CA"}; tplD = new String[]{"571", "658"}; tplC = new String[]{"758", "571"};
        }
        Spinner spTpl = new Spinner(this);
        spTpl.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tplNames));
        box.addView(spTpl);

        Spinner spJ = new Spinner(this);
        final String[] journals = {"OD", "CA", "BQ", "AC", "VT"};
        spJ.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, journals));
        if (dbl) box.addView(spJ);

        TextView tvDate = new TextView(this);
        final String[] date = {new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())};
        tvDate.setText("Date : " + date[0]);
        tvDate.setPadding(0, dp(10), 0, dp(10));
        tvDate.setTextColor(0xFF1E40AF);
        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new android.app.DatePickerDialog(this, (dp0, y, m, d) -> {
                date[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                tvDate.setText("Date : " + date[0]);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        box.addView(tvDate);

        EditText etLabel = input("Libellé", InputType.TYPE_CLASS_TEXT);
        EditText etDebit = input("Compte débité (ex. 571)", InputType.TYPE_CLASS_NUMBER);
        EditText etCredit = input("Compte crédité (ex. 101)", InputType.TYPE_CLASS_NUMBER);
        EditText etAmount = input("Montant", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(etLabel);
        if (dbl) { box.addView(etDebit); box.addView(etCredit); }
        box.addView(etAmount);

        spTpl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                for (int k = 0; k < journals.length; k++) if (journals[k].equals(tplJ[i])) spJ.setSelection(k);
                etDebit.setText(tplD[i]); etCredit.setText(tplC[i]);
                if (i > 0 || !dbl) etLabel.setText(tplNames[i]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        new AlertDialog.Builder(this)
                .setTitle(dbl ? "Nouvelle écriture (partie double)" : "Mouvement de caisse")
                .setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String label = etLabel.getText().toString().trim();
                    String deb = dbl ? etDebit.getText().toString().trim() : tplD[spTpl.getSelectedItemPosition()];
                    String cre = dbl ? etCredit.getText().toString().trim() : tplC[spTpl.getSelectedItemPosition()];
                    double amt;
                    try { amt = Double.parseDouble(etAmount.getText().toString().trim().replace(',', '.')); }
                    catch (Exception e) { amt = 0; }
                    if (label.isEmpty() || amt <= 0 || deb.isEmpty() || cre.isEmpty()) {
                        Toast.makeText(this, "Libellé, comptes et montant requis", Toast.LENGTH_LONG).show(); return;
                    }
                    if (deb.equals(cre)) { Toast.makeText(this, "Comptes débit et crédit identiques", Toast.LENGTH_LONG).show(); return; }
                    svc.addManual(date[0], dbl ? journals[spJ.getSelectedItemPosition()] : "CA", label, deb, cre, amt);
                    Toast.makeText(this, "Écriture enregistrée", Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton("Annuler", null).show();
    }

    private void addAccountDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText code = input("Numéro de compte (ex. 4111)", InputType.TYPE_CLASS_NUMBER);
        EditText lab = input("Intitulé", InputType.TYPE_CLASS_TEXT);
        box.addView(code); box.addView(lab);
        new AlertDialog.Builder(this).setTitle("Ajouter un compte").setView(box)
                .setPositiveButton("Ajouter", (d, w) -> {
                    String c = code.getText().toString().trim(), l = lab.getText().toString().trim();
                    if (c.length() < 2 || l.isEmpty()) { Toast.makeText(this, "Numéro (2 chiffres min.) et intitulé requis", Toast.LENGTH_LONG).show(); return; }
                    svc.addAccount(c, l); reload();
                }).setNegativeButton("Annuler", null).show();
    }

    // ── Adapter ─────────────────────────────────────────────────────────────
    private class TableAdapter extends RecyclerView.Adapter<TableAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            LinearLayout row = new LinearLayout(AccountingActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, dp(7), 0, dp(7));
            return new VH(row);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            LinearLayout row = (LinearLayout) h.itemView;
            row.removeAllViews();
            boolean bold = current.bold.get(pos);
            row.setBackgroundColor(bold ? 0xFFE8EEFB : (pos % 2 == 0 ? 0xFFFFFFFF : 0xFFF8FAFC));
            String[] r = current.rows.get(pos);
            for (int c = 0; c < r.length; c++) row.addView(cell(r[c], current.weights[c], current.right[c], bold, 0xFF0F172A));
            final long id = current.ids.get(pos);
            if (id < 0) { row.setOnClickListener(null); row.setClickable(false); }
            else row.setOnClickListener(v -> new AlertDialog.Builder(AccountingActivity.this)
                    .setTitle("Supprimer cette écriture ?").setMessage(r[2])
                    .setPositiveButton("Supprimer", (d, w) -> { svc.deleteManual(id); reload(); })
                    .setNegativeButton("Annuler", null).show());
        }
        @Override public int getItemCount() { return current == null ? 0 : current.rows.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
