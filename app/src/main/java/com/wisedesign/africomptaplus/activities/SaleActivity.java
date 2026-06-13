package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.*;
import com.wisedesign.africomptaplus.services.*;
import java.util.*;

public class SaleActivity extends AppCompatActivity {

    private ProductService productService;
    private SaleService    saleService;
    private InvoiceManager invoiceManager;
    private ClientService  clientService;

    private List<SaleItem> cart = new ArrayList<>();
    private CartAdapter    cartAdapter;
    private TextView       tvTotal, tvClientSelected;
    private RadioGroup     rgPayment;
    private EditText       etDueDate;

    // Client sélectionné (0 = passager)
    private long   selectedClientId    = 0;
    private String selectedClientName  = "Client Passager";
    private String selectedClientPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale);

        productService = new ProductService(this);
        saleService    = new SaleService(this);
        invoiceManager = new InvoiceManager(this);
        clientService  = new ClientService(this);

        tvTotal         = findViewById(R.id.tvTotal);
        tvClientSelected= findViewById(R.id.tvClientSelected);
        rgPayment       = findViewById(R.id.rgPayment);
        etDueDate       = findViewById(R.id.etDueDate);

        RecyclerView rv = findViewById(R.id.rvCart);
        rv.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter(cart);
        rv.setAdapter(cartAdapter);

        updateClientLabel();

        findViewById(R.id.btnSelectClient).setOnClickListener(v -> showClientPicker());
        findViewById(R.id.btnAddProduct).setOnClickListener(v -> showProductPicker());
        findViewById(R.id.btnFinalizeSale).setOnClickListener(v -> finalizeSale());

        // Afficher/masquer champ échéance selon mode paiement
        rgPayment.setOnCheckedChangeListener((g, id) -> {
            etDueDate.setVisibility(id == R.id.rbCredit ? android.view.View.VISIBLE : android.view.View.GONE);
        });
    }

    // ── Sélection client ──────────────────────────────────────────────────────
    private void showClientPicker() {
        List<Client> clients = clientService.findAll();

        // Option "Client Passager" + liste
        String[] names = new String[clients.size() + 2];
        names[0] = "👤 Client Passager";
        names[1] = "➕ Nouveau client rapide…";
        for (int i = 0; i < clients.size(); i++) names[i + 2] = clients.get(i).toString();

        new AlertDialog.Builder(this)
                .setTitle("Sélectionner le client")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        selectedClientId    = 0;
                        selectedClientName  = "Client Passager";
                        selectedClientPhone = "";
                        updateClientLabel();
                    } else if (which == 1) {
                        showQuickClientDialog();
                    } else {
                        Client c = clients.get(which - 2);
                        selectedClientId    = c.id;
                        selectedClientName  = c.name;
                        selectedClientPhone = c.phone != null ? c.phone : "";
                        updateClientLabel();
                    }
                }).show();
    }

    private void showQuickClientDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_quick_client, null);
        EditText etName  = v.findViewById(R.id.etClientName);
        EditText etPhone = v.findViewById(R.id.etClientPhone);
        new AlertDialog.Builder(this)
                .setTitle("Nouveau client")
                .setView(v)
                .setPositiveButton("Créer", (d, w) -> {
                    String name  = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show(); return; }
                    Client c  = new Client(name, phone);
                    long   id = clientService.save(c);
                    selectedClientId    = id;
                    selectedClientName  = name;
                    selectedClientPhone = phone;
                    updateClientLabel();
                })
                .setNegativeButton("Annuler", null).show();
    }

    private void updateClientLabel() {
        if (tvClientSelected != null)
            tvClientSelected.setText("Client : " + selectedClientName
                + (selectedClientPhone.isEmpty() ? "" : " · " + selectedClientPhone));
    }

    // ── Produits ──────────────────────────────────────────────────────────────
    private void showProductPicker() {
        List<Product> products = productService.findAll();
        if (products.isEmpty()) {
            Toast.makeText(this, "Aucun produit. Ajoutez d'abord des produits.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[products.size()];
        for (int i = 0; i < products.size(); i++)
            names[i] = products.get(i).name + " — " + (int)products.get(i).sellingPrice + " XAF (Stock:" + products.get(i).stock + ")";

        new AlertDialog.Builder(this)
                .setTitle("Choisir un produit")
                .setItems(names, (d, w) -> showQuantityDialog(products.get(w)))
                .show();
    }

    private void showQuantityDialog(Product product) {
        EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText("1");
        new AlertDialog.Builder(this)
                .setTitle("Quantité — " + product.name)
                .setView(et)
                .setPositiveButton("Ajouter", (d, w) -> {
                    int qty;
                    try { qty = Integer.parseInt(et.getText().toString().trim()); }
                    catch (Exception e) { qty = 1; }
                    if (qty <= 0) { Toast.makeText(this, "Quantité invalide", Toast.LENGTH_SHORT).show(); return; }
                    if (qty > product.stock) {
                        Toast.makeText(this, "Stock insuffisant (" + product.stock + " dispo)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addToCart(product, qty);
                })
                .setNegativeButton("Annuler", null).show();
    }

    private void addToCart(Product p, int qty) {
        for (SaleItem item : cart) {
            if (item.productId == p.id) {
                item.quantity  += qty;
                item.totalPrice = item.unitPrice * item.quantity;
                cartAdapter.notifyDataSetChanged();
                updateTotal();
                return;
            }
        }
        cart.add(new SaleItem(p.id, p.name, p.buyingPrice, qty, p.sellingPrice));
        cartAdapter.notifyDataSetChanged();
        updateTotal();
    }

    private void updateTotal() {
        double total = 0;
        for (SaleItem item : cart) total += item.totalPrice;
        tvTotal.setText(String.format(Locale.FRENCH, "TOTAL : %,.0f XAF", total));
    }

    // ── Finalisation ──────────────────────────────────────────────────────────
    private void finalizeSale() {
        if (cart.isEmpty()) { Toast.makeText(this, "Panier vide", Toast.LENGTH_SHORT).show(); return; }

        int checkedId = rgPayment.getCheckedRadioButtonId();
        String method = checkedId == R.id.rbMobileMoney ? "mobile_money"
                      : checkedId == R.id.rbCredit      ? "credit"
                      : "cash";

        // Crédit sans client nommé → avertissement
        if ("credit".equals(method) && selectedClientId == 0) {
            Toast.makeText(this, "Sélectionnez un client pour une vente à crédit", Toast.LENGTH_LONG).show();
            return;
        }

        String dueDate = etDueDate != null ? etDueDate.getText().toString().trim() : "";

        long saleId = saleService.createSale(method, cart,
                selectedClientId, selectedClientName, selectedClientPhone, dueDate);

        if (saleId < 0) {
            Toast.makeText(this, "Erreur enregistrement vente", Toast.LENGTH_SHORT).show();
            return;
        }

        Sale sale = saleService.findByIdWithItems(saleId);
        if (sale != null) {
            Uri pdfUri = invoiceManager.generateInvoicePDF(sale, sale.items);
            if (pdfUri != null) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/pdf");
                share.putExtra(Intent.EXTRA_STREAM, pdfUri);
                share.putExtra(Intent.EXTRA_SUBJECT, "Facture " + sale.invoiceNumber);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Partager la facture via"));
            }
        }

        Toast.makeText(this, "Vente enregistrée !", Toast.LENGTH_SHORT).show();
        cart.clear(); cartAdapter.notifyDataSetChanged(); updateTotal();
        selectedClientId = 0; selectedClientName = "Client Passager"; selectedClientPhone = "";
        updateClientLabel();
    }

    // ── Cart Adapter ──────────────────────────────────────────────────────────
    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
        private final List<SaleItem> list;
        CartAdapter(List<SaleItem> l) { list = l; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_cart, p, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SaleItem item = list.get(pos);
            h.tvName.setText(item.productName);
            h.tvQty.setText("x" + item.quantity);
            h.tvPrice.setText(String.format(Locale.FRENCH, "%,.0f XAF", item.totalPrice));
            h.tvRemove.setOnClickListener(v -> {
                list.remove(pos); notifyDataSetChanged(); updateTotal();
            });
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvQty, tvPrice, tvRemove;
            VH(View v) {
                super(v);
                tvName   = v.findViewById(R.id.tvCartProductName);
                tvQty    = v.findViewById(R.id.tvCartQty);
                tvPrice  = v.findViewById(R.id.tvCartPrice);
                tvRemove = v.findViewById(R.id.tvCartRemove);
            }
        }
    }
}
