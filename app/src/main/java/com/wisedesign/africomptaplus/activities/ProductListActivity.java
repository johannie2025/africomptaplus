package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Product;
import com.wisedesign.africomptaplus.services.ProductService;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

public class ProductListActivity extends AppCompatActivity {

    private ProductService productService;
    private ProductAdapter adapter;
    private final List<Product> productList = new ArrayList<>();
    private TextView tvCount, tvEmpty;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);
        UiKit.topBar(this, "Produits");
        UiKit.topAction(this, "Ajouter", R.drawable.ic_plus,
                v -> startActivity(new Intent(this, ProductFormActivity.class)));

        productService = new ProductService(this);
        tvCount = findViewById(R.id.tvProductCount);
        tvEmpty = findViewById(R.id.tvEmpty);

        RecyclerView rv = findViewById(R.id.rvProducts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter(productList);
        rv.setAdapter(adapter);

        etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { loadProducts(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        BottomNav.attach(this, BottomNav.PRODUCTS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts(etSearch.getText().toString());
    }

    private void loadProducts(String query) {
        productList.clear();
        productList.addAll(query.isEmpty() ? productService.findAll() : productService.search(query));
        adapter.notifyDataSetChanged();
        tvCount.setText(productList.size() + " produit(s)");
        tvEmpty.setVisibility(productList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private final List<Product> list;
        ProductAdapter(List<Product> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Product p = list.get(pos);
            h.tvName.setText(p.name);
            h.tvPrice.setText(UiKit.xaf(p.sellingPrice));
            h.tvBarcode.setText(p.barcode != null && !p.barcode.isEmpty() ? "SKU : " + p.barcode : "");
            h.tvBarcode.setVisibility(h.tvBarcode.getText().length() == 0 ? View.GONE : View.VISIBLE);

            boolean low = p.isLowStock();
            h.tvStock.setText((low ? "Stock faible : " : "Stock : ") + p.stock);
            h.tvStock.setBackgroundResource(low ? R.drawable.bg_pill_danger : R.drawable.bg_pill_success);
            h.tvStock.setTextColor(getResources().getColor(low ? R.color.error : R.color.success));

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ProductListActivity.this, ProductFormActivity.class);
                i.putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, p.id);
                startActivity(i);
            });
            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(ProductListActivity.this)
                        .setTitle("Supprimer " + p.name + " ?")
                        .setPositiveButton("Oui", (d, w) -> {
                            productService.delete(p.id);
                            loadProducts(etSearch.getText().toString());
                            Toast.makeText(ProductListActivity.this, "Produit supprimé", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Non", null).show();
                return true;
            });
            UiKit.pressScale(h.itemView);
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvStock, tvBarcode;
            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tvProductName);
                tvPrice   = v.findViewById(R.id.tvProductPrice);
                tvStock   = v.findViewById(R.id.tvProductStock);
                tvBarcode = v.findViewById(R.id.tvProductBarcode);
            }
        }
    }
}
