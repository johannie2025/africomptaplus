package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.*;
import com.wisedesign.africomptaplus.services.*;
import java.util.*;

public class SaleActivity extends AppCompatActivity {

    private ProductService productService;
    private SaleService    saleService;
    private InvoiceManager invoiceManager;
    private ClientService  clientService;
    private PendingSaleService pendingSaleService;

    private List<SaleItem> cart = new ArrayList<>();
    private CartAdapter    cartAdapter;
    private TextView       tvTotal, tvClientSelected;
    private TextView       tvSubtotal, tvDiscountAmount, tvVatAmount;
    private RadioGroup     rgPayment;
    private RadioGroup     rgDiscountType;
    private EditText       etDueDate;
    private EditText       etDiscountValue;
    private boolean        configuredVatEnabled;
    private boolean        vatEnabled;
    private double         configuredVatRate;
    private double         vatRate;

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
        pendingSaleService = new PendingSaleService(this);
        DatabaseHelper configDb = DatabaseHelper.getInstance(this);
        configuredVatEnabled = "1".equals(configDb.getConfig("vat_enabled"));
        configuredVatRate = parseNumber(configDb.getConfig("vat_rate"));
        selectedClientName = getString(R.string.client_guest);
        vatEnabled = configuredVatEnabled;
        vatRate = configuredVatRate;

        tvTotal         = findViewById(R.id.tvTotal);
        tvClientSelected= findViewById(R.id.tvClientSelected);
        rgPayment       = findViewById(R.id.rgPayment);
        rgDiscountType  = findViewById(R.id.rgDiscountType);
        etDueDate       = findViewById(R.id.etDueDate);
        etDiscountValue = findViewById(R.id.etDiscountValue);
        tvSubtotal      = findViewById(R.id.tvSubtotal);
        tvDiscountAmount = findViewById(R.id.tvDiscountAmount);
        tvVatAmount     = findViewById(R.id.tvVatAmount);

        RecyclerView rv = findViewById(R.id.rvCart);
        rv.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter(cart);
        rv.setAdapter(cartAdapter);

        updateClientLabel();

        findViewById(R.id.btnSelectClient).setOnClickListener(v -> showClientPicker());
        findViewById(R.id.btnAddProduct).setOnClickListener(v -> showProductPicker());
        findViewById(R.id.btnScanBarcode).setOnClickListener(v -> scanBarcode());
        findViewById(R.id.btnHoldSale).setOnClickListener(v -> holdSale());
        findViewById(R.id.btnResumeSale).setOnClickListener(v -> showPendingSales());
        findViewById(R.id.btnFinalizeSale).setOnClickListener(v -> finalizeSale());

        // Afficher/masquer champ échéance selon mode paiement
        rgPayment.setOnCheckedChangeListener((g, id) -> {
            etDueDate.setVisibility(id == R.id.rbCredit ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        rgDiscountType.setOnCheckedChangeListener((g, id) -> updateTotal());
        etDiscountValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateTotal(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateTotal();
    }

    // ── Sélection client ──────────────────────────────────────────────────────
    private void showClientPicker() {
        List<Client> clients = clientService.findAll();

        // Option "Client Passager" + liste
        String[] names = new String[clients.size() + 2];
        names[0] = getString(R.string.client_guest_option);
        names[1] = getString(R.string.client_quick_create);
        for (int i = 0; i < clients.size(); i++) names[i + 2] = clients.get(i).toString();

        new AlertDialog.Builder(this)
                .setTitle(R.string.client_select_title)
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        selectedClientId    = 0;
                        selectedClientName  = getString(R.string.client_guest);
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
                .setTitle(R.string.client_new_title)
                .setView(v)
                .setPositiveButton(R.string.client_create, (d, w) -> {
                    String name  = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, R.string.client_name_required, Toast.LENGTH_SHORT).show(); return; }
                    Client c  = new Client(name, phone);
                    long   id = clientService.save(c);
                    selectedClientId    = id;
                    selectedClientName  = name;
                    selectedClientPhone = phone;
                    updateClientLabel();
                })
                .setNegativeButton(R.string.btn_cancel, null).show();
    }

    private void updateClientLabel() {
        if (tvClientSelected != null)
            tvClientSelected.setText(getString(R.string.sale_client_label, selectedClientName)
                + (selectedClientPhone.isEmpty() ? "" : " · " + selectedClientPhone));
    }

    // ── Produits ──────────────────────────────────────────────────────────────
    private void showProductPicker() {
        List<Product> products = productService.findAll();
        if (products.isEmpty()) {
            Toast.makeText(this, R.string.sale_no_products, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[products.size()];
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            names[i] = getString(R.string.sale_product_option, product.name,
                    String.format(Locale.getDefault(), "%,.0f", product.sellingPrice), product.stock);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.sale_choose_product_title)
                .setItems(names, (d, w) -> showQuantityDialog(products.get(w)))
                .show();
    }

    private void showQuantityDialog(Product product) {
        EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText("1");
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sale_quantity_title, product.name))
                .setView(et)
                .setPositiveButton(R.string.sale_add, (d, w) -> {
                    int qty;
                    try { qty = Integer.parseInt(et.getText().toString().trim()); }
                    catch (Exception e) { qty = 1; }
                    if (qty <= 0) { Toast.makeText(this, R.string.sale_quantity_invalid, Toast.LENGTH_SHORT).show(); return; }
                    if (qty > product.stock) {
                        Toast.makeText(this, getString(R.string.sale_stock_available, product.stock), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addToCart(product, qty);
                })
                .setNegativeButton(R.string.btn_cancel, null).show();
    }

    private void addToCart(Product p, int qty) {
        for (SaleItem item : cart) {
            if (item.productId == p.id) {
                if (item.quantity + qty > p.stock) {
                    Toast.makeText(this, getString(R.string.sale_stock_available, p.stock), Toast.LENGTH_SHORT).show();
                    return;
                }
                item.quantity  += qty;
                item.totalPrice = item.unitPrice * item.quantity;
                cartAdapter.notifyDataSetChanged();
                updateTotal();
                return;
            }
        }
        if (qty > p.stock) {
            Toast.makeText(this, getString(R.string.sale_stock_available, p.stock), Toast.LENGTH_SHORT).show();
            return;
        }
        cart.add(new SaleItem(p.id, p.name, p.buyingPrice, qty, p.sellingPrice));
        cartAdapter.notifyDataSetChanged();
        updateTotal();
    }

    private void updateTotal() {
        double subtotal = getSubtotal();
        double discount = calculateDiscount(subtotal);
        double tax = calculateVat(subtotal, discount);
        double total = subtotal - discount + tax;
        tvSubtotal.setText(getString(R.string.sale_subtotal, String.format(Locale.getDefault(), "%,.0f", subtotal)));
        tvDiscountAmount.setText(getString(R.string.sale_discount_summary, String.format(Locale.getDefault(), "%,.0f", discount)));
        tvVatAmount.setText(vatEnabled
                ? getString(R.string.sale_vat_summary, String.format(Locale.getDefault(), "%.2f", vatRate),
                    String.format(Locale.getDefault(), "%,.0f", tax))
            : getString(R.string.sale_vat_disabled));
        tvTotal.setText(getString(R.string.sale_total_label, String.format(Locale.getDefault(), "%,.0f", total)));
        tvTotal.setScaleX(0.96f);
        tvTotal.setScaleY(0.96f);
        tvTotal.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    private double getSubtotal() {
        double subtotal = 0;
        for (SaleItem item : cart) subtotal += item.totalPrice;
        return subtotal;
    }

    private String currentDiscountType() {
        return rgDiscountType.getCheckedRadioButtonId() == R.id.rbDiscountAmount ? "amount" : "percent";
    }

    private double getDiscountValue() {
        return parseNumber(etDiscountValue.getText().toString().trim());
    }

    private double calculateDiscount(double subtotal) {
        double value = getDiscountValue();
        double discount = "percent".equals(currentDiscountType()) ? subtotal * value / 100d : value;
        return Math.min(subtotal, Math.max(0, Math.round(discount)));
    }

    private double calculateVat(double subtotal, double discount) {
        return vatEnabled ? Math.round((subtotal - discount) * vatRate / 100d) : 0;
    }

    private double parseNumber(String value) {
        try { return value == null || value.trim().isEmpty() ? 0 : Double.parseDouble(value.replace(',', '.')); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void scanBarcode() {
        new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
                .setPrompt(getString(R.string.sale_scan_prompt))
                .setBeepEnabled(true)
                .setOrientationLocked(false)
                .initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, R.string.sale_scan_cancelled, Toast.LENGTH_SHORT).show();
            } else {
                Product product = productService.findByBarcode(result.getContents().trim());
                if (product == null) {
                    Toast.makeText(this, R.string.sale_scan_error_product, Toast.LENGTH_LONG).show();
                } else if (product.stock < 1) {
                    Toast.makeText(this, R.string.sale_out_of_stock, Toast.LENGTH_LONG).show();
                } else {
                    addToCart(product, 1);
                    Toast.makeText(this, getString(R.string.sale_added_short, product.name), Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String currentPaymentMethod() {
        int checkedId = rgPayment.getCheckedRadioButtonId();
        return checkedId == R.id.rbMobileMoney ? "mobile_money"
                : checkedId == R.id.rbCredit ? "credit" : "cash";
    }

    private void showPendingSales() {
        List<PendingSale> drafts = pendingSaleService.findAll();
        if (drafts.isEmpty()) {
            Toast.makeText(this, R.string.sale_pending_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[drafts.size()];
        for (int i = 0; i < drafts.size(); i++) {
            PendingSale draft = drafts.get(i);
            double subtotal = 0;
            for (SaleItem item : draft.items) subtotal += item.totalPrice;
            double discount = "percent".equals(draft.discountType)
                ? subtotal * draft.discountValue / 100d : draft.discountValue;
            discount = Math.min(subtotal, Math.max(0, Math.round(discount)));
            double vat = draft.vatEnabled
                ? Math.round((subtotal - discount) * draft.vatRate / 100d) : 0;
            double total = subtotal - discount + vat;
            labels[i] = draft.clientName + " · " + String.format(Locale.getDefault(), "%,.0f XAF", total)
                    + "\n" + draft.createdAt;
        }
        new AlertDialog.Builder(this).setTitle(R.string.sale_pending_title)
                .setItems(labels, (dialog, which) -> showDraftActions(drafts.get(which)))
            .setNegativeButton(R.string.btn_close, null).show();
    }

    private void showDraftActions(PendingSale draft) {
        new AlertDialog.Builder(this).setTitle(draft.clientName)
                .setItems(new String[]{getString(R.string.sale_resume_action), getString(R.string.btn_delete)}, (dialog, which) -> {
                    if (which == 0) confirmResume(draft);
                    else new AlertDialog.Builder(this).setMessage(R.string.sale_delete_pending_confirm)
                            .setNegativeButton(R.string.btn_cancel, null)
                            .setPositiveButton(R.string.btn_delete, (d, w) -> {
                                pendingSaleService.delete(draft.id);
                                Toast.makeText(this, R.string.sale_pending_deleted, Toast.LENGTH_SHORT).show();
                            }).show();
                }).setNegativeButton(R.string.btn_cancel, null).show();
    }

    private void confirmResume(PendingSale draft) {
        if (cart.isEmpty()) {
            resumeDraft(draft);
            return;
        }
        new AlertDialog.Builder(this).setMessage(R.string.sale_switch_confirm)
            .setNegativeButton(R.string.btn_cancel, null)
            .setNeutralButton(R.string.sale_replace, (dialog, which) -> resumeDraft(draft))
            .setPositiveButton(R.string.sale_hold, (dialog, which) -> {
                    if (!holdSale()) return;
                    resumeDraft(draft);
                }).show();
    }

    private void resumeDraft(PendingSale draft) {
        cart.clear();
        cart.addAll(draft.items);
        selectedClientId = draft.clientId;
        selectedClientName = draft.clientName;
        selectedClientPhone = draft.clientPhone;
        updateClientLabel();
        rgPayment.check("mobile_money".equals(draft.paymentMethod) ? R.id.rbMobileMoney
                : "credit".equals(draft.paymentMethod) ? R.id.rbCredit : R.id.rbCash);
        rgDiscountType.check("amount".equals(draft.discountType) ? R.id.rbDiscountAmount : R.id.rbDiscountPercent);
        etDiscountValue.setText(String.valueOf(draft.discountValue));
        vatEnabled = draft.vatEnabled;
        vatRate = draft.vatRate;
        etDueDate.setText(draft.dueDate);
        cartAdapter.notifyDataSetChanged();
        updateTotal();
        pendingSaleService.delete(draft.id);
        Toast.makeText(this, R.string.sale_resumed, Toast.LENGTH_SHORT).show();
    }

    // ── Finalisation ──────────────────────────────────────────────────────────
    private void finalizeSale() {
        if (cart.isEmpty()) { Toast.makeText(this, R.string.sale_cart_empty, Toast.LENGTH_SHORT).show(); return; }

        String method = currentPaymentMethod();

        // Crédit sans client nommé → avertissement
        if ("credit".equals(method) && selectedClientId == 0) {
            Toast.makeText(this, R.string.sale_credit_client_required, Toast.LENGTH_LONG).show();
            return;
        }

        double discountValue = getDiscountValue();
        double subtotal = getSubtotal();
        if (discountValue < 0 || ("percent".equals(currentDiscountType()) && discountValue > 100)
            || ("amount".equals(currentDiscountType()) && discountValue > subtotal)) {
            Toast.makeText(this, R.string.sale_discount_invalid, Toast.LENGTH_LONG).show();
            return;
        }

        String dueDate = etDueDate != null ? etDueDate.getText().toString().trim() : "";

        long saleId = saleService.createSale(method, cart,
            selectedClientId, selectedClientName, selectedClientPhone, dueDate,
            currentDiscountType(), discountValue, vatEnabled, vatRate);

        if (saleId == -2) {
            Toast.makeText(this, R.string.sale_stock_recheck, Toast.LENGTH_LONG).show();
            return;
        }
        if (saleId == -3) {
            Toast.makeText(this, R.string.sale_vat_discount_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        if (saleId < 0) {
            Toast.makeText(this, R.string.sale_record_error, Toast.LENGTH_SHORT).show();
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

        Toast.makeText(this, R.string.sale_recorded, Toast.LENGTH_SHORT).show();
        clearCurrentSale();
    }

    private boolean holdSale() {
        if (cart.isEmpty()) {
            Toast.makeText(this, R.string.sale_hold_empty_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        long id = pendingSaleService.save(cart, selectedClientId, selectedClientName,
            selectedClientPhone, currentPaymentMethod(), etDueDate.getText().toString().trim(),
            currentDiscountType(), getDiscountValue(), vatEnabled, vatRate);
        if (id < 0) {
            Toast.makeText(this, R.string.sale_hold_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        clearCurrentSale();
        Toast.makeText(this, R.string.sale_held, Toast.LENGTH_SHORT).show();
        return true;
    }

    private void clearCurrentSale() {
        cart.clear(); cartAdapter.notifyDataSetChanged();
        selectedClientId = 0; selectedClientName = getString(R.string.client_guest); selectedClientPhone = "";
        rgPayment.check(R.id.rbCash);
        etDueDate.setText("");
        rgDiscountType.check(R.id.rbDiscountPercent);
        etDiscountValue.setText("");
        vatEnabled = configuredVatEnabled;
        vatRate = configuredVatRate;
        updateClientLabel();
        updateTotal();
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
            h.tvPrice.setText(String.format(Locale.getDefault(), "%,.0f XAF", item.totalPrice));
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
