package com.wisedesign.africomptaplus.activities;

import com.wisedesign.africomptaplus.ui.I18n;

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
public class AccountingActivity extends BaseActivity {
    private AccountingService svc;
    private String mode, q = "", from = "", to = "";
    private String[] tabs, keys;
    private int tab = 0;
    private ReportTable current;
    private final List<TextView> tabViews = new ArrayList<>();
    private TableAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_accounting);
        UiKit.topBar(this, I18n.t("Comptabilité"));
        BottomNav.attach(this, BottomNav.ACCOUNTING);
        svc = new AccountingService(this);
        mode = svc.mode();
        if ("off".equals(mode)) {
            Toast.makeText(this, I18n.t("Activez la comptabilité dans Réglages"), Toast.LENGTH_LONG).show();
            finish(); return;
        }
        keys = "double".equals(mode)
                ? new String[]{"Journal", "Grand livre", "Balance", "Résultat", "Bilan", "Analytique", "Inventaire", "Mouvements", "Comptages", "Plan", "Saisies"}
                : new String[]{"Livre de caisse", "Synthèse", "Inventaire", "Mouvements", "Comptages", "Saisies"};
        tabs = new String[keys.length];
        for (int i = 0; i < keys.length; i++) tabs[i] = I18n.t(keys[i]);

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
        String want = getIntent().getStringExtra("tab");
        if (want != null) for (int i = 0; i < keys.length; i++) if (keys[i].equals(want)) tab = i;
        FilterBar.attach(this, (qq, f, t) -> { q = qq; from = f; to = t; reload(); });
        findViewById(R.id.btnAddEntry).setOnClickListener(v -> {
            String k = keys[tab];
            if ("Plan".equals(k)) addAccountDialog();
            else if ("Mouvements".equals(k)) moveDialog();
            else if ("Inventaire".equals(k) || "Comptages".equals(k)) startActivity(new Intent(this, InventoryCountActivity.class));
            else entryDialog();
        });
        findViewById(R.id.btnExportTable).setOnClickListener(v -> export());
        UiKit.pressScale(findViewById(R.id.btnAddEntry), findViewById(R.id.btnExportTable));
        reload();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private ReportTable build() {
        switch (keys[tab]) {
            case "Journal":        return svc.journal(from, to, q);
            case "Grand livre":    return svc.ledger(from, to, q);
            case "Balance":        return svc.balance(from, to, q);
            case "Résultat":       return svc.income(from, to);
            case "Bilan":          return svc.balanceSheet(to);
            case "Analytique":     return svc.analytic(from, to);
            case "Plan":           return svc.plan(q);
            case "Saisies":        return svc.manualList(from, to, q);
            case "Inventaire":     return svc.inventory(q);
            case "Mouvements":     return svc.stockCard(0, from, to, q);
            case "Comptages":      return svc.counts(from, to);
            case "Synthèse":       return svc.simpleSummary(from, to);
            default:               return svc.cashBook(from, to, q);
        }
    }

    private String subtitle() {
        String p = (from.isEmpty() && to.isEmpty()) ? "Toutes périodes"
                : "Du " + (from.isEmpty() ? "début" : from) + " au " + (to.isEmpty() ? "ce jour" : to);
        if ("Bilan".equals(keys[tab])) p = "Situation cumulée " + (to.isEmpty() ? "à ce jour" : "au " + to);
        return p + " · " + ("double".equals(mode) ? "Comptabilité générale & analytique (SYSCOHADA)" : "Comptabilité simple");
    }

    @Override protected void onResume() { super.onResume(); if (current != null) reload(); }

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
        ((TextView) findViewById(R.id.btnAddEntry)).setText("Plan".equals(keys[tab]) ? I18n.t("+ Compte") : "Mouvements".equals(keys[tab]) ? I18n.t("+ Mouvement") : ("Inventaire".equals(keys[tab]) || "Comptages".equals(keys[tab])) ? I18n.t("+ Comptage") : I18n.t("+ Écriture"));
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
        Uri u = TablePdf.export(this, current, subtitle(), "compta_" + keys[tab].replace(' ', '_') + "_" + System.currentTimeMillis());
        if (u == null) { Toast.makeText(this, I18n.t("Erreur de génération du PDF"), Toast.LENGTH_SHORT).show(); return; }
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
            tplNames = new String[]{I18n.t("Écriture libre"), I18n.t("Apport en capital"), I18n.t("Achat de marchandises comptant"), I18n.t("Achat de marchandises à crédit"),
                    "Règlement fournisseur", "Dépôt en banque", "Retrait personnel (exploitant)", "Emprunt reçu", "Paiement de loyer"};
            tplJ = new String[]{"OD", "CA", "AC", "AC", "CA", "BQ", "CA", "BQ", "AC"};
            tplD = new String[]{"", "571", "311", "311", "401", "521", "108", "521", "6222"};
            tplC = new String[]{"", "101", "571", "401", "571", "571", "571", "162", "571"};
        } else {
            tplNames = new String[]{I18n.t("Entrée de caisse"), I18n.t("Sortie de caisse")};
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
        tvDate.setText(I18n.t("Date : ") + date[0]);
        tvDate.setPadding(0, dp(10), 0, dp(10));
        tvDate.setTextColor(0xFF1E40AF);
        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new android.app.DatePickerDialog(this, (dp0, y, m, d) -> {
                date[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                tvDate.setText(I18n.t("Date : ") + date[0]);
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
                .setTitle(dbl ? I18n.t("Nouvelle écriture (partie double)") : I18n.t("Mouvement de caisse"))
                .setView(box)
                .setPositiveButton(I18n.t("Enregistrer"), (d, w) -> {
                    String label = etLabel.getText().toString().trim();
                    String deb = dbl ? etDebit.getText().toString().trim() : tplD[spTpl.getSelectedItemPosition()];
                    String cre = dbl ? etCredit.getText().toString().trim() : tplC[spTpl.getSelectedItemPosition()];
                    double amt;
                    try { amt = Double.parseDouble(etAmount.getText().toString().trim().replace(',', '.')); }
                    catch (Exception e) { amt = 0; }
                    if (label.isEmpty() || amt <= 0 || deb.isEmpty() || cre.isEmpty()) {
                        Toast.makeText(this, I18n.t("Libellé, comptes et montant requis"), Toast.LENGTH_LONG).show(); return;
                    }
                    if (deb.equals(cre)) { Toast.makeText(this, "Comptes débit et crédit identiques", Toast.LENGTH_LONG).show(); return; }
                    svc.addManual(date[0], dbl ? journals[spJ.getSelectedItemPosition()] : "CA", label, deb, cre, amt);
                    Toast.makeText(this, I18n.t("Écriture enregistrée"), Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(I18n.t("Annuler"), null).show();
    }

    private void moveDialog() {
        final List<long[]> ps = new ArrayList<>(); final List<String> names = new ArrayList<>(); final List<Double> costs = new ArrayList<>();
        Map<Long, Double> cmp = svc.cmpMap();
        android.database.Cursor c = com.wisedesign.africomptaplus.db.DatabaseHelper.getInstance(this).getReadableDatabase()
                .rawQuery("SELECT id, name, stock, buying_price FROM products WHERE archived=0 ORDER BY name COLLATE NOCASE", null);
        try { while (c.moveToNext()) { ps.add(new long[]{c.getLong(0), c.getInt(2)}); names.add(c.getString(1) + " (" + c.getInt(2) + ")"); Double cm = cmp.get(c.getLong(0)); costs.add(cm != null ? cm : c.getDouble(3)); } } finally { c.close(); }
        if (ps.isEmpty()) { Toast.makeText(this, I18n.t("Produit introuvable"), Toast.LENGTH_LONG).show(); return; }
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(8), dp(20), 0);
        Spinner spP = new Spinner(this); spP.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        Spinner spK = new Spinner(this); spK.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{I18n.t("Entrée"), I18n.t("Sortie")}));
        final String[] rs = {"Retour client", "Don reçu", "Correction de stock", "Casse / perte", "Vol", "Usage personnel", "Don", "Péremption", "Retour fournisseur"};
        final String[] rsT = new String[rs.length]; for (int i = 0; i < rs.length; i++) rsT[i] = I18n.t(rs[i]);
        Spinner spR = new Spinner(this); spR.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, rsT));
        EditText etQ = input(I18n.t("Qté"), InputType.TYPE_CLASS_NUMBER);
        EditText etC = input(I18n.t("Coût unitaire (entrée)"), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etN = input(I18n.t("Note (facultatif)"), InputType.TYPE_CLASS_TEXT);
        TextView hint = new TextView(this); hint.setText(I18n.t("Pour un réapprovisionnement fournisseur, utilisez un achat (Fournisseurs) : stock, CMP, dette et écritures sont mis à jour.")); hint.setTextSize(11);
        box.addView(hint); box.addView(spP); box.addView(spK); box.addView(etQ); box.addView(etC); box.addView(spR); box.addView(etN);
        spP.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) { etC.setText(String.valueOf(Math.round(costs.get(i)))); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        spK.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) { etC.setEnabled(i == 0); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        new AlertDialog.Builder(this).setTitle(I18n.t("Mouvement de stock")).setView(box)
                .setPositiveButton(I18n.t("Enregistrer"), (d, w) -> {
                    int qty; double cost;
                    try { qty = Integer.parseInt(etQ.getText().toString().trim()); } catch (Exception e) { qty = 0; }
                    try { cost = Double.parseDouble(etC.getText().toString().trim().replace(',', '.')); } catch (Exception e) { cost = 0; }
                    String err = svc.saveMove(ps.get(spP.getSelectedItemPosition())[0], spK.getSelectedItemPosition() == 1, qty, cost, rs[spR.getSelectedItemPosition()], etN.getText().toString().trim());
                    if (err != null) { Toast.makeText(this, err, Toast.LENGTH_LONG).show(); return; }
                    Toast.makeText(this, I18n.t("Mouvement enregistré ✓"), Toast.LENGTH_SHORT).show();
                    com.wisedesign.africomptaplus.services.SyncService sy = new com.wisedesign.africomptaplus.services.SyncService(this); if (sy.enabled()) sy.syncNow(null);
                    reload();
                }).setNegativeButton(I18n.t("Annuler"), null).show();
    }

    private void addAccountDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText code = input("Numéro de compte (ex. 4111)", InputType.TYPE_CLASS_NUMBER);
        EditText lab = input("Intitulé", InputType.TYPE_CLASS_TEXT);
        box.addView(code); box.addView(lab);
        new AlertDialog.Builder(this).setTitle(I18n.t("Ajouter un compte")).setView(box)
                .setPositiveButton(I18n.t("Ajouter"), (d, w) -> {
                    String c = code.getText().toString().trim(), l = lab.getText().toString().trim();
                    if (c.length() < 2 || l.isEmpty()) { Toast.makeText(this, I18n.t("Numéro (2 chiffres min.) et intitulé requis"), Toast.LENGTH_LONG).show(); return; }
                    svc.addAccount(c, l); reload();
                }).setNegativeButton(I18n.t("Annuler"), null).show();
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
            else if ("Comptages".equals(keys[tab])) row.setOnClickListener(v -> new AlertDialog.Builder(AccountingActivity.this)
                    .setTitle(r[0]).setMessage(svc.countDetail(id))
                    .setPositiveButton("OK", null)
                    .setNeutralButton("PDF", (d, w) -> {
                        com.wisedesign.africomptaplus.services.ReportTable ct = svc.countTable(id);
                        // Line 333
Uri u = TablePdf.export(AccountingActivity.this, ct, ct.title, "inventaire_" + id + "_" + System.currentTimeMillis());

// Line 334
if (u == null) { 
    Toast.makeText(AccountingActivity.this, I18n.t("Erreur de génération du PDF"), Toast.LENGTH_SHORT).show(); 
    return; 
}
                        Intent s = new Intent(Intent.ACTION_SEND); s.setType("application/pdf"); s.putExtra(Intent.EXTRA_STREAM, u); s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(s, "Partager l'état"));
                    }).show());
            else row.setOnClickListener(v -> new AlertDialog.Builder(AccountingActivity.this)
                    .setTitle(I18n.t("Supprimer cette écriture ?")).setMessage(r[2])
                    .setPositiveButton(I18n.t("Supprimer"), (d, w) -> { svc.deleteManual(id); reload(); })
                    .setNegativeButton(I18n.t("Annuler"), null).show());
        }
        @Override public int getItemCount() { return current == null ? 0 : current.rows.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
