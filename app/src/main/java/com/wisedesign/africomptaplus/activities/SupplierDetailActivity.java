package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Purchase;
import com.wisedesign.africomptaplus.models.SaleItem;
import com.wisedesign.africomptaplus.models.Supplier;
import com.wisedesign.africomptaplus.services.SupplierService;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.FilterBar;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/** Fiche fournisseur : soldes, historique des achats (recherche + période), règlements. */
public class SupplierDetailActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "supplier_id";
    private SupplierService svc;
    private long id;
    private final List<Purchase> list = new ArrayList<>();
    private Adapter adapter;
    private String q = "", from = "", to = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_supplier_detail);
        UiKit.topBar(this, "Fournisseur");
        BottomNav.attach(this, BottomNav.NONE);
        svc = new SupplierService(this);
        id = getIntent().getLongExtra(EXTRA_ID, -1);
        RecyclerView rv = findViewById(R.id.rvPurchases);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        FilterBar.attach(this, (qq, f, t) -> { q = qq; from = f; to = t; load(); });
        findViewById(R.id.btnNewPurchase).setOnClickListener(v -> startActivity(
                new Intent(this, PurchaseActivity.class).putExtra(PurchaseActivity.EXTRA_SUPPLIER_ID, id)));
        findViewById(R.id.btnEditSupplier).setOnClickListener(v -> {
            Supplier s = svc.find(id);
            if (s != null) SupplierActivity.editDialog(this, svc, s, this::load);
        });
        UiKit.pressScale(findViewById(R.id.btnNewPurchase), findViewById(R.id.btnEditSupplier));
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        Supplier s = svc.find(id);
        if (s == null) { finish(); return; }
        ((TextView) findViewById(R.id.tvSupDetName)).setText(s.name);
        ((TextView) findViewById(R.id.tvSupDetInfo)).setText((s.phone + (s.address.isEmpty() ? "" : " · " + s.address)).trim());
        List<Purchase> all = svc.purchases(id, "", "", "");
        double tot = 0, paid = 0;
        for (Purchase p : all) { tot += p.total; paid += p.paid; }
        ((TextView) findViewById(R.id.tvSupTotal)).setText(UiKit.xaf(tot));
        ((TextView) findViewById(R.id.tvSupPaid)).setText(UiKit.xaf(paid));
        ((TextView) findViewById(R.id.tvSupRemaining)).setText(UiKit.xaf(tot - paid));
        list.clear(); list.addAll(svc.purchases(id, q, from, to));
        adapter.notifyDataSetChanged();
        findViewById(R.id.tvEmpty).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void actions(Purchase p) {
        List<String> l = new ArrayList<>();
        l.add("Voir le détail");
        if (p.remaining() > 0) l.add("Régler (paiement)");
        new AlertDialog.Builder(this).setTitle(p.reference)
                .setItems(l.toArray(new String[0]), (d, w) -> { if (w == 0) detail(p.id); else payDialog(p); }).show();
    }

    private void detail(long purchaseId) {
        Purchase p = svc.findPurchase(purchaseId);
        if (p == null) return;
        StringBuilder sb = new StringBuilder();
        for (SaleItem i : p.items) sb.append(i.quantity).append(" × ").append(i.productName).append(" @ ").append(UiKit.fmt(i.unitPrice))
                .append(" = ").append(UiKit.xaf(i.totalPrice)).append("\n");
        sb.append("\nTotal : ").append(UiKit.xaf(p.total)).append("\nPayé : ").append(UiKit.xaf(p.paid))
          .append("\nReste : ").append(UiKit.xaf(p.remaining()));
        List<String> pays = svc.paymentLines(purchaseId);
        if (p.initialPaid > 0) sb.append("\n\nAcompte : ").append(UiKit.xaf(p.initialPaid));
        if (!pays.isEmpty()) { sb.append("\n\nRèglements :"); for (String s : pays) sb.append("\n• ").append(s); }
        if (!p.notes.isEmpty()) sb.append("\n\nNote : ").append(p.notes);
        new AlertDialog.Builder(this).setTitle(p.reference + " — " + p.createdAt).setMessage(sb.toString())
                .setPositiveButton("Fermer", null).show();
    }

    private void payDialog(Purchase p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 8, pad, 0);
        EditText amount = new EditText(this);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("Montant"); amount.setText(String.valueOf((long) p.remaining()));
        box.addView(amount);
        RadioGroup rg = new RadioGroup(this); rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rc = new RadioButton(this); rc.setText("Espèces"); rc.setId(View.generateViewId());
        RadioButton rm = new RadioButton(this); rm.setText("Mobile Money"); rm.setId(View.generateViewId());
        rg.addView(rc); rg.addView(rm); rg.check(rc.getId());
        box.addView(rg);
        new AlertDialog.Builder(this).setTitle("Règlement — " + p.supplierName)
                .setMessage("Reste dû : " + UiKit.xaf(p.remaining())).setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    double a;
                    try { a = Double.parseDouble(amount.getText().toString().trim().replace(',', '.')); } catch (Exception e) { a = 0; }
                    double done = svc.pay(p.id, a, rg.getCheckedRadioButtonId() == rm.getId() ? "mobile_money" : "cash", "");
                    if (done <= 0) Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show();
                    else { Toast.makeText(this, "Règlement de " + UiKit.xaf(done) + " enregistré", Toast.LENGTH_LONG).show(); load(); }
                }).setNegativeButton("Annuler", null).show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_sale, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Purchase p = list.get(pos);
            h.ref.setText(p.reference);
            h.client.setText(p.remaining() > 0 ? "Reste dû : " + UiKit.xaf(p.remaining()) : "Entièrement payé");
            h.date.setText(p.createdAt);
            h.amount.setText(UiKit.xaf(p.total));
            h.tag.setText(p.remaining() <= 0 ? "Payé" : p.paid > 0 ? "Partiel" : "À payer");
            UiKit.pressScale(h.itemView);
            h.itemView.setOnClickListener(v -> actions(p));
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView ref, client, date, amount, tag;
            VH(View v) { super(v); ref = v.findViewById(R.id.tvSaleInvoice); client = v.findViewById(R.id.tvSaleClient);
                date = v.findViewById(R.id.tvSaleDate); amount = v.findViewById(R.id.tvSaleAmount); tag = v.findViewById(R.id.tvSalePayment); }
        }
    }
}
