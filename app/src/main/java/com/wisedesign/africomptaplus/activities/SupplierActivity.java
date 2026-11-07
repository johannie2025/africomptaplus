package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Supplier;
import com.wisedesign.africomptaplus.services.SupplierService;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/** Liste des fournisseurs avec solde dû ; création / édition. */
public class SupplierActivity extends AppCompatActivity {
    private SupplierService svc;
    private final List<Supplier> list = new ArrayList<>();
    private Adapter adapter;
    private String q = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_suppliers);
        UiKit.topBar(this, "Fournisseurs");
        BottomNav.attach(this, BottomNav.NONE);
        svc = new SupplierService(this);
        RecyclerView rv = findViewById(R.id.rvSuppliers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        ((EditText) findViewById(R.id.etSupSearch)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { q = s.toString(); load(); }
        });
        findViewById(R.id.btnAddSupplier).setOnClickListener(v -> edit(new Supplier()));
        UiKit.pressScale(findViewById(R.id.btnAddSupplier));
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        list.clear(); list.addAll(svc.list(q));
        adapter.notifyDataSetChanged();
        ((TextView) findViewById(R.id.tvTotalPayable)).setText(UiKit.xaf(svc.totalPayable()));
        ((TextView) findViewById(R.id.tvSupplierCount)).setText(list.size() + " fournisseur(s)");
        findViewById(R.id.tvEmpty).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Dialogue de création / modification (réutilisé par SupplierDetailActivity). */
    static void editDialog(android.app.Activity a, SupplierService svc, Supplier s, Runnable done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(p, 8, p, 0);
        EditText name = field(a, "Nom du fournisseur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, s.name);
        EditText phone = field(a, "Téléphone", InputType.TYPE_CLASS_PHONE, s.phone);
        EditText email = field(a, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, s.email);
        EditText addr = field(a, "Adresse", InputType.TYPE_CLASS_TEXT, s.address);
        EditText notes = field(a, "Notes", InputType.TYPE_CLASS_TEXT, s.notes);
        box.addView(name); box.addView(phone); box.addView(email); box.addView(addr); box.addView(notes);
        AlertDialog.Builder bd = new AlertDialog.Builder(a)
                .setTitle(s.id > 0 ? "Modifier le fournisseur" : "Nouveau fournisseur").setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { Toast.makeText(a, "Nom obligatoire", Toast.LENGTH_SHORT).show(); return; }
                    s.name = n; s.phone = phone.getText().toString().trim(); s.email = email.getText().toString().trim();
                    s.address = addr.getText().toString().trim(); s.notes = notes.getText().toString().trim();
                    svc.save(s); done.run();
                })
                .setNegativeButton("Annuler", null);
        if (s.id > 0) bd.setNeutralButton("Supprimer", (d, w) -> {
            if (svc.delete(s.id)) { done.run(); a.finish(); }
            else Toast.makeText(a, "Suppression impossible : ce fournisseur a des achats", Toast.LENGTH_LONG).show();
        });
        bd.show();
    }

    private static EditText field(android.content.Context c, String hint, int type, String value) {
        EditText e = new EditText(c); e.setHint(hint); e.setInputType(type); e.setText(value); return e;
    }

    private void edit(Supplier s) { editDialog(this, svc, s, this::load); }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_supplier, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Supplier s = list.get(pos);
            h.init.setText(UiKit.initials(s.name));
            h.name.setText(s.name);
            h.info.setText((s.phone.isEmpty() ? "" : s.phone + " · ") + s.purchaseCount + " achat(s)");
            h.due.setText(s.due > 0 ? UiKit.xaf(s.due) : "Soldé");
            h.due.setTextColor(getResources().getColor(s.due > 0 ? R.color.accent : R.color.success));
            UiKit.pressScale(h.itemView);
            h.itemView.setOnClickListener(v -> startActivity(new Intent(SupplierActivity.this, SupplierDetailActivity.class)
                    .putExtra(SupplierDetailActivity.EXTRA_ID, s.id)));
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView init, name, info, due;
            VH(View v) { super(v); init = v.findViewById(R.id.tvSupInitials); name = v.findViewById(R.id.tvSupName);
                info = v.findViewById(R.id.tvSupInfo); due = v.findViewById(R.id.tvSupDue); }
        }
    }
}
