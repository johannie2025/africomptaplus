package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Product;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.models.SaleItem;
import com.wisedesign.africomptaplus.services.InvoiceManager;
import com.wisedesign.africomptaplus.services.ProductService;
import com.wisedesign.africomptaplus.services.SaleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SaleActivity — Point de vente (caisse).
 * Permet de composer un panier, choisir le mode de paiement et finaliser la vente.
 */
public class SaleActivity extends AppCompatActivity {

    private ProductService productService;
    private SaleService    saleService;
    private InvoiceManager invoiceManager;

    private List<SaleItem> cart = new ArrayList<>();
    private CartAdapter    cartAdapter;
    private TextView       tvTotal;
    private RadioGroup     rgPayment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale);

        productService = new ProductService(this);
        saleService    = new SaleService(this);
        invoiceManager = new InvoiceManager(this);

        tvTotal = findViewById(R.id.tvTotal);
        rgPayment = findViewById(R.id.rgPayment);

        RecyclerView rvCart = findViewById(R.id.rvCart);
        rvCart.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter(cart);
        rvCart.setAdapter(cartAdapter);

        // Bouton ajouter produit
        Button btnAddProduct = findViewById(R.id.btnAddProduct);
        btnAddProduct.setOnClickListener(v -> showProductPicker());

        // Bouton finaliser
        Button btnFinalize = findViewById(R.id.btnFinalizeSale);
        btnFinalize.setOnClickListener(v -> finalizeSale());
    }

    // ── Sélection produit ─────────────────────────────────────────────────────

    private void showProductPicker() {
        List<Product> products = productService.findAll();
        if (products.isEmpty()) {
            Toast.makeText(this, "Aucun produit disponible. Ajoutez d'abord des produits.", Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[products.size()];
        for (int i = 0; i < products.size(); i++) {
            names[i] = products.get(i).name + " (" + (int) products.get(i).sellingPrice + " XAF) — Stock: " + products.get(i).stock;
        }

        new AlertDialog.Builder(this)
                .setTitle("Choisir un produit")
                .setItems(names, (dialog, which) -> showQuantityDialog(products.get(which)))
                .show();
    }

    private void showQuantityDialog(Product product) {
        EditText etQty = new EditText(this);
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etQty.setText("1");
        etQty.setHint("Quantité");

        new AlertDialog.Builder(this)
                .setTitle("Quantité : " + product.name)
                .setView(etQty)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    String qtyStr = etQty.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);
                    if (qty <= 0) {
                        Toast.makeText(this, "Quantité invalide", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (qty > product.stock) {
                        Toast.makeText(this, "Stock insuffisant (" + product.stock + " dispo)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addToCart(product, qty);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void addToCart(Product product, int qty) {
        // Si le produit est déjà dans le panier, on additionne
        for (SaleItem item : cart) {
            if (item.productId == product.id) {
                item.quantity   += qty;
                item.totalPrice  = item.unitPrice * item.quantity;
                cartAdapter.notifyDataSetChanged();
                updateTotal();
                return;
            }
        }
        // Nouveau produit
        SaleItem item = new SaleItem(product.id, product.name, product.buyingPrice,
                qty, product.sellingPrice);
        cart.add(item);
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
        if (cart.isEmpty()) {
            Toast.makeText(this, "Panier vide", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mode de paiement
        int checkedId = rgPayment.getCheckedRadioButtonId();
        String paymentMethod;
        if (checkedId == R.id.rbMobileMoney) {
            paymentMethod = "mobile_money";
        } else if (checkedId == R.id.rbCredit) {
            paymentMethod = "credit";
        } else {
            paymentMethod = "cash";
        }

        long saleId = saleService.createSale(paymentMethod, cart);
        if (saleId < 0) {
            Toast.makeText(this, "Erreur lors de l'enregistrement de la vente", Toast.LENGTH_SHORT).show();
            return;
        }

        // Génération de la facture PDF
        Sale sale = saleService.findByIdWithItems(saleId);
        if (sale != null) {
            Uri pdfUri = invoiceManager.generateInvoicePDF(sale, sale.items);
            if (pdfUri != null) {
                sharePDF(pdfUri, sale.invoiceNumber);
            }
        }

        Toast.makeText(this, "Vente enregistrée !", Toast.LENGTH_SHORT).show();
        cart.clear();
        cartAdapter.notifyDataSetChanged();
        updateTotal();
    }

    private void sharePDF(Uri pdfUri, String invoiceNumber) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Facture " + invoiceNumber);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Partager la facture via"));
    }

    // ── Adapter panier ────────────────────────────────────────────────────────

    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {

        private final List<SaleItem> list;

        CartAdapter(List<SaleItem> list) { this.list = list; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cart, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SaleItem item = list.get(pos);
            h.tvName.setText(item.productName);
            h.tvQty.setText("x" + item.quantity);
            h.tvPrice.setText(String.format(Locale.FRENCH, "%,.0f XAF", item.totalPrice));

            h.tvRemove.setOnClickListener(v -> {
                list.remove(pos);
                notifyDataSetChanged();
                updateTotal();
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
