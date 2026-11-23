package com.wisedesign.africomptaplus.activities;

import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.services.AccountingService;
import com.wisedesign.africomptaplus.services.SyncService;
import com.wisedesign.africomptaplus.ui.I18n;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.*;

/** Inventaire physique : saisie des quantités comptées, écart en direct, validation (ajuste le stock + écriture 6031/311). */
public class InventoryCountActivity extends BaseActivity {
    private final List<long[]> ids = new ArrayList<>();            // {id, stock}
    private final List<String> names = new ArrayList<>();
    private final Map<Long, Integer> counts = new LinkedHashMap<>();
    private LinearLayout list;
    private TextView tvSum;
    private String filter = "";

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF8FAFC);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        setContentView(root);
        UiKit.topBar(this, I18n.t("Nouvel inventaire physique"));

        EditText search = new EditText(this);
        search.setHint(I18n.t("Rechercher un produit"));
        search.setSingleLine(true);
        root.addView(search);
        EditText note = new EditText(this);
        note.setHint(I18n.t("Note (facultatif)"));
        note.setSingleLine(true);
        root.addView(note);
        tvSum = new TextView(this);
        tvSum.setPadding(0, dp(6), 0, dp(6));
        root.addView(tvSum);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button ok = new Button(this);
        ok.setText(I18n.t("Valider l'inventaire"));
        root.addView(ok);

        Cursor c = DatabaseHelper.getInstance(this).getReadableDatabase()
                .rawQuery("SELECT id, name, stock FROM products WHERE archived=0 ORDER BY name COLLATE NOCASE", null);
        try { while (c.moveToNext()) { ids.add(new long[]{c.getLong(0), c.getInt(2)}); names.add(c.getString(1)); } } finally { c.close(); }

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c2) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c2) {}
            public void afterTextChanged(Editable e) { filter = e.toString().trim().toLowerCase(Locale.ROOT); render(); }
        });
        ok.setOnClickListener(v -> {
            if (counts.isEmpty()) { Toast.makeText(this, I18n.t("Aucune quantité saisie"), Toast.LENGTH_LONG).show(); return; }
            new AlertDialog.Builder(this).setTitle(I18n.t("Valider l'inventaire"))
                    .setMessage(counts.size() + " " + I18n.t("Articles") + "\n" + summary())
                    .setPositiveButton(I18n.t("Valider l'inventaire"), (d, w) -> {
                        long id = new AccountingService(this).saveCount(note.getText().toString().trim(), counts);
                        if (id < 0) { Toast.makeText(this, I18n.t("Erreur"), Toast.LENGTH_LONG).show(); return; }
                        Toast.makeText(this, I18n.t("Inventaire enregistré ✓"), Toast.LENGTH_LONG).show();
                        SyncService sy = new SyncService(this); if (sy.enabled()) sy.syncNow(null);
                        finish();
                    }).setNegativeButton(I18n.t("Annuler"), null).show();
        });
        render();
    }

    private String summary() {
        long dq = 0;
        for (long[] p : ids) { Integer n = counts.get(p[0]); if (n != null) dq += n - p[1]; }
        return I18n.t("Écart qté") + " : " + (dq > 0 ? "+" : "") + dq;
    }

    private void render() {
        list.removeAllViews();
        tvSum.setText(counts.isEmpty() ? "" : summary());
        for (int i = 0; i < ids.size(); i++) {
            if (!filter.isEmpty() && !names.get(i).toLowerCase(Locale.ROOT).contains(filter)) continue;
            final long pid = ids.get(i)[0]; final int theo = (int) ids.get(i)[1];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(6), 0, dp(6));
            TextView n = new TextView(this);
            n.setText(names.get(i) + "\n" + I18n.t("Théorique") + " : " + theo);
            row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            EditText e = new EditText(this);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setHint(I18n.t("Compté"));
            e.setMinEms(4);
            if (counts.containsKey(pid)) e.setText(String.valueOf(counts.get(pid)));
            TextView var = new TextView(this);
            var.setMinEms(3);
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b2, int c2) {}
                public void onTextChanged(CharSequence s, int a, int b2, int c2) {}
                public void afterTextChanged(Editable ed) {
                    String t = ed.toString().trim();
                    if (t.isEmpty()) { counts.remove(pid); var.setText(""); }
                    else try { int v = Integer.parseInt(t); counts.put(pid, v); var.setText((v - theo > 0 ? "+" : "") + (v - theo)); } catch (Exception x) { counts.remove(pid); }
                    tvSum.setText(counts.isEmpty() ? "" : summary());
                }
            });
            row.addView(e); row.addView(var);
            list.addView(row);
        }
    }
}
