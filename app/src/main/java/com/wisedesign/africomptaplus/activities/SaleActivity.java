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
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;
import java.util.*;

/** Point de vente : client · articles (± orange) · mode de paiement · Encaisser. Logique XAF inchangée. */
public class SaleActivity extends AppCompatActivity {

    private ProductService productService;
    private SaleService    saleService;
    private InvoiceManager invoiceManager;
    private ClientService  clientService;

    private final List<SaleItem> cart = new ArrayList<>();
    private CartAdapter    cartAdapter;
    private TextView       tvTotal, tvClientSelected, tvClientPhone, tvClientAvatar, tvArticlesTitle, tvEmptyCart;
    private EditText       etDueDate;
    private View           payCash, payMobile, payCredit;
    private String         paymentMethod = "cash";

    private long   selectedClientId    = 0;
    private String selectedClientName  = "Client Passager";
    private String selectedClientPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale);
        UiKit.topBar(this, "Point de vente");

        productService = new ProductService(this);
        saleService    = new SaleService(this);
        invoiceManager = new InvoiceManager(this);
        clientService  = new ClientService(this);

        tvTotal          = findViewById(R.id.tvTotal);
        tvClientSelected = findViewById(R.id.tvClientSelected);
        tvClientPhone    = findViewById(R.id.tvClientPhone);
        tvClientAvatar   = findViewById(R.id.tvClientAvatar);
        tvArticlesTitle  = findViewById(R.id.tvArticlesTitle);
        tvEmptyCart      = findViewById(R.id.tvEmptyCart);
        etDueDate        = findViewById(R.id.etDueDate);
        payCash   = findViewById(R.id.payCash);
        payMobile = findViewById(R.id.payMobile);
        payCredit = findViewById(R.id.payCredit);

        RecyclerView rv = findViewById(R.id.rvCart);
        rv.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter(cart);
        rv.setAdapter(cartAdapter);

        findViewById(R.id.btnSelectClient).setOnClickListener(v -> showClientPicker());
        findViewById(R.id.btnNewClient).setOnClickListener(v -> showQuickClientDialog());
        findViewById(R.id.btnAddProduct).setOnClickListener(v -> showProductPicker());
        findViewById(R.id.btnFinalizeSale).setOnClickListener(v -> finalizeSale());
        UiKit.pressScale(findViewById(R.id.btnFinalizeSale), payCash, payMobile, payCredit);

        payCash.setOnClickListener(v -> selectPayment("cash"));
        payMobile.setOnClickListener(v -> selectPayment("mobile_money"));
        payCredit.setOnClickListener(v -> selectPayment("credit"));
        selectPayment("cash");

        updateClientLabel();
        refreshCart();
        BottomNav.attach(this, BottomNav.SALE);
    }

    private void selectPayment(String m) {
        paymentMethod = m;
        payCash.setSelected("cash".equals(m));
        payMobile.setSelected("mobile_money".equals(m));
        payCredit.setSelected("credit".equals(m));
        etDueDate.setVisibility("credit".equals(m) ? View.VISIBLE : View.GONE);
    }

    // ── Sélection client ──────────────────────────────────────────────────────
    private void showClientPicker() {
        List<Client> clients = clientService.findAll();
        String[] names = new String[clients.size() + 1];
        names[0] = "Client Passager";
        for (int i = 0; i < clients.size(); i++) names[i + 1] = clients.get(i).toString();

        new AlertDialog.Builder(this)
                .setTitle("Sélectionner le client")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        selectedClientId = 0; selectedClientName = "Client Passager"; selectedClientPhone = "";
                    } else {
                        Client c = clients.get(which - 1);
                        selectedClientId    = c.id;
                        selectedClientName  = c.name;
                        selectedClientPhone = c.phone != null ? c.phone : "";
                    }
                    updateClientLabel();
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
                    long id = clientService.save(new Client(name, phone));
                    selectedClientId = id; selectedClientName = name; selectedClientPhone = phone;
                    updateClientLabel();
                })
                .setNegativeButton("Annuler", null).show();
    }

    private void updateClientLabel() {
        tvClientSelected.setText(selectedClientName);
        tvClientPhone.setText(selectedClientPhone.isEmpty() ? "Vente comptoir" : selectedClientPhone);
        tvClientAvatar.setText(UiKit.initials(selectedClientName));
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
            names[i] = products.get(i).name + " — " + UiKit.xaf(products.get(i).sellingPrice)
                     + "  (stock " + products.get(i).stock + ")";

        new AlertDialog.Builder(this)
                .setTitle("Choisir un produit")
                .setItems(names, (d, w) -> showQuantityDialog(products.get(w)))
                .show();
    }

    private void showQuantityDialog(Product product) {
        EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText("1");
        FrameLayout box = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("Quantité — " + product.name)
                .setView(box)
                .setPositiveButton("Ajouter", (d, w) -> {
                    int qty;
                    try { qty = Integer.parseInt(et.getText().toString().trim()); }
                    catch (Exception e) { qty = 1; }
                    if (qty <= 0) { Toast.makeText(this, "Quantité invalide", Toast.LENGTH_SHORT).show(); return; }
                    if (qty + qtyInCart(product.id) > product.stock) {
                        Toast.makeText(this, "Stock insuffisant (" + product.stock + " dispo)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addToCart(product, qty);
                })
                .setNegativeButton("Annuler", null).show();
    }

    private int qtyInCart(long productId) {
        for (SaleItem i : cart) if (i.productId == productId) return i.quantity;
        return 0;
    }

    private void addToCart(Product p, int qty) {
        for (SaleItem item : cart) {
            if (item.productId == p.id) {
                item.quantity  += qty;
                item.totalPrice = item.unitPrice * item.quantity;
                refreshCart();
                return;
            }
        }
        cart.add(new SaleItem(p.id, p.name, p.buyingPrice, qty, p.sellingPrice));
        refreshCart();
    }

    private void refreshCart() {
        cartAdapter.notifyDataSetChanged();
        double total = 0;
        for (SaleItem item : cart) total += item.totalPrice;
        tvTotal.setText(UiKit.xaf(total));
        tvArticlesTitle.setText("Articles (" + cart.size() + ")");
        tvEmptyCart.setVisibility(cart.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── Finalisation ──────────────────────────────────────────────────────────
    private void finalizeSale() {
        if (cart.isEmpty()) { Toast.makeText(this, "Panier vide", Toast.LENGTH_SHORT).show(); return; }

        if ("credit".equals(paymentMethod) && selectedClientId == 0) {
            Toast.makeText(this, "Sélectionnez un client pour une vente à crédit", Toast.LENGTH_LONG).show();
            return;
        }

        String dueDate = etDueDate.getText().toString().trim();

        long saleId = saleService.createSale(paymentMethod, cart,
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
        cart.clear();
        selectedClientId = 0; selectedClientName = "Client Passager"; selectedClientPhone = "";
        etDueDate.setText("");
        selectPayment("cash");
        updateClientLabel();
        refreshCart();
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
            h.tvUnit.setText(UiKit.xaf(item.unitPrice) + " / unité");
            h.tvQty.setText(String.valueOf(item.quantity));
            h.tvPrice.setText(UiKit.xaf(item.totalPrice));

            h.btnMinus.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                SaleItem it = list.get(p);
                if (it.quantity <= 1) list.remove(p);
                else { it.quantity--; it.totalPrice = it.unitPrice * it.quantity; }
                refreshCart();
            });
            h.btnPlus.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                SaleItem it = list.get(p);
                Product prod = productService.findById(it.productId);
                if (prod != null && it.quantity + 1 > prod.stock) {
                    Toast.makeText(SaleActivity.this, "Stock insuffisant (" + prod.stock + " dispo)", Toast.LENGTH_SHORT).show();
                    return;
                }
                it.quantity++; it.totalPrice = it.unitPrice * it.quantity;
                refreshCart();
            });
            UiKit.pressScale(h.btnMinus, h.btnPlus);
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvUnit, tvQty, tvPrice;
            View btnMinus, btnPlus;
            VH(View v) {
                super(v);
                tvName   = v.findViewById(R.id.tvCartProductName);
                tvUnit   = v.findViewById(R.id.tvCartUnit);
                tvQty    = v.findViewById(R.id.tvCartQty);
                tvPrice  = v.findViewById(R.id.tvCartPrice);
                btnMinus = v.findViewById(R.id.btnQtyMinus);
                btnPlus  = v.findViewById(R.id.btnQtyPlus);
            }
        }
    }
}
