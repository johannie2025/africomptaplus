package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Product;
import com.wisedesign.africomptaplus.models.SaleItem;
import com.wisedesign.africomptaplus.models.Supplier;
import com.wisedesign.africomptaplus.services.ProductService;
import com.wisedesign.africomptaplus.services.SupplierService;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/** Nouvel achat : lignes (produit, quantité, coût unitaire), acompte, reste dû au fournisseur. Entre en stock + écritures comptables. */
public class PurchaseActivity extends AppCompatActivity {
    public static final String EXTRA_SUPPLIER_ID = "supplier_id";
    private SupplierService svc;
    private ProductService products;
    private Supplier supplier;
    private final List<SaleItem> lines = new ArrayList<>();
    private LinearLayout llLines;
    private EditText etPaid;
    private boolean paidTouched = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_purchase);
        UiKit.topBar(this, "Nouvel achat");
        svc = new SupplierService(this);
        products = new ProductService(this);
        supplier = svc.find(getIntent().getLongExtra(EXTRA_SUPPLIER_ID, -1));
        if (supplier == null) { finish(); return; }
        ((TextView) findViewById(R.id.tvPurSupplier)).setText("Fournisseur : " + supplier.name);
        llLines = findViewById(R.id.llLines);
        etPaid = findViewById(R.id.etPurPaid);
        etPaid.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { updateRemaining(); }
        });
        etPaid.setOnFocusChangeListener((v, f) -> { if (f) paidTouched = true; });
        findViewById(R.id.btnAddLine).setOnClickListener(v -> pickProduct());
        findViewById(R.id.btnSavePurchase).setOnClickListener(v -> save());
        UiKit.pressScale(findViewById(R.id.btnAddLine), findViewById(R.id.btnSavePurchase));
        refresh();
    }

    private double total() { double t = 0; for (SaleItem i : lines) t += i.quantity * i.unitPrice; return t; }
    private double paid() {
        try { return Double.parseDouble(etPaid.getText().toString().trim().replace(',', '.')); } catch (Exception e) { return 0; }
    }

    private void refresh() {
        llLines.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (int idx = 0; idx < lines.size(); idx++) {
            SaleItem i = lines.get(idx);
            TextView t = new TextView(this);
            t.setText(i.quantity + " × " + i.productName + "\n" + UiKit.xaf(i.unitPrice) + " / unité  →  " + UiKit.xaf(i.quantity * i.unitPrice) + "   (appui long : retirer)");
            t.setTextSize(13);
            t.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (10 * d));
            t.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * d);
            t.setLayoutParams(lp);
            final int at = idx;
            t.setOnLongClickListener(v -> { lines.remove(at); refresh(); return true; });
            llLines.addView(t);
        }
        ((TextView) findViewById(R.id.tvPurTotal)).setText("Total : " + UiKit.xaf(total()));
        if (!paidTouched) etPaid.setText(total() > 0 ? String.valueOf((long) total()) : "");
        updateRemaining();
    }

    private void updateRemaining() {
        double rest = Math.max(0, total() - paid());
        ((TextView) findViewById(R.id.tvPurRemaining)).setText(rest > 0 ? "Reste dû au fournisseur : " + UiKit.xaf(rest) : "Payé en totalité");
    }

    private void pickProduct() {
        final List<Product> all = new ArrayList<>(products.findAll());
        final List<Product> shown = new ArrayList<>(all);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText search = new EditText(this);
        search.setHint("Rechercher un produit");
        box.addView(search);
        ListView lv = new ListView(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lv.setAdapter(ad);
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (320 * getResources().getDisplayMetrics().density)));
        Runnable fill = () -> {
            ad.clear();
            for (Product p : shown) ad.add(p.name + "  ·  stock " + p.stock + "  ·  achat " + UiKit.fmt(p.buyingPrice));
            ad.notifyDataSetChanged();
        };
        fill.run();
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Produit acheté").setView(box)
                .setNegativeButton("Annuler", null).create();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) {
                shown.clear();
                String q = s.toString().trim().toLowerCase();
                for (Product p : all) if (q.isEmpty() || p.name.toLowerCase().contains(q) || (p.barcode != null && p.barcode.contains(q))) shown.add(p);
                fill.run();
            }
        });
        lv.setOnItemClickListener((parent, v, pos, id) -> { dlg.dismiss(); qtyCost(shown.get(pos)); });
        dlg.show();
    }

    private void qtyCost(Product p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 8, pad, 0);
        EditText qty = new EditText(this); qty.setHint("Quantité reçue"); qty.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText cost = new EditText(this); cost.setHint("Coût d'achat unitaire");
        cost.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (p.buyingPrice > 0) cost.setText(String.valueOf((long) p.buyingPrice));
        box.addView(qty); box.addView(cost);
        new AlertDialog.Builder(this).setTitle(p.name).setView(box)
                .setPositiveButton("Ajouter", (d, w) -> {
                    int q; double c;
                    try { q = Integer.parseInt(qty.getText().toString().trim()); c = Double.parseDouble(cost.getText().toString().trim().replace(',', '.')); }
                    catch (Exception e) { Toast.makeText(this, "Quantité et coût requis", Toast.LENGTH_SHORT).show(); return; }
                    if (q <= 0 || c < 0) { Toast.makeText(this, "Valeurs invalides", Toast.LENGTH_SHORT).show(); return; }
                    lines.add(new SaleItem(p.id, p.name, p.buyingPrice, q, c));
                    refresh();
                }).setNegativeButton("Annuler", null).show();
    }

    private void save() {
        if (lines.isEmpty()) { Toast.makeText(this, "Ajoutez au moins un produit", Toast.LENGTH_SHORT).show(); return; }
        double total = total(), paid = paid();
        if (paid < 0 || paid > total) { Toast.makeText(this, "Le montant payé doit être entre 0 et " + UiKit.xaf(total), Toast.LENGTH_LONG).show(); return; }
        RadioGroup rg = findViewById(R.id.rgPurMethod);
        String method = rg.getCheckedRadioButtonId() == R.id.rbPurMobile ? "mobile_money" : "cash";
        long id = svc.createPurchase(supplier, lines, paid, method,
                ((EditText) findViewById(R.id.etPurNotes)).getText().toString().trim(),
                ((CheckBox) findViewById(R.id.cbUpdateCost)).isChecked());
        if (id < 0) { Toast.makeText(this, "Erreur d'enregistrement", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Achat enregistré — stock mis à jour", Toast.LENGTH_LONG).show();
        finish();
    }
}
