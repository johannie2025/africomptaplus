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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Product;
import com.wisedesign.africomptaplus.services.ProductService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductListActivity extends AppCompatActivity {

    private ProductService    productService;
    private ProductAdapter    adapter;
    private List<Product>     productList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        productService = new ProductService(this);

        RecyclerView rv = findViewById(R.id.rvProducts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter(productList);
        rv.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadProducts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        FloatingActionButton fab = findViewById(R.id.fabAddProduct);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, ProductFormActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts("");
    }

    private void loadProducts(String query) {
        productList.clear();
        if (query.isEmpty()) {
            productList.addAll(productService.findAll());
        } else {
            productList.addAll(productService.search(query));
        }
        adapter.notifyDataSetChanged();
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {

        private final List<Product> list;

        ProductAdapter(List<Product> list) { this.list = list; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Product p = list.get(pos);
            h.tvName.setText(p.name);
            h.tvPrice.setText(String.format(Locale.getDefault(), "%,.0f XAF", p.sellingPrice));
            h.tvStock.setText(getString(R.string.badge_stock_display, p.stock));
            h.tvStock.setTextColor(p.isLowStock() ? 0xFFE53935 : 0xFF43A047);

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ProductListActivity.this, ProductFormActivity.class);
                i.putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, p.id);
                startActivity(i);
            });

            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(ProductListActivity.this)
                        .setTitle(getString(R.string.dialog_delete_product, p.name))
                        .setPositiveButton(getString(R.string.action_yes), (d, w) -> {
                            productService.delete(p.id);
                            loadProducts("");
                            Toast.makeText(ProductListActivity.this, getString(R.string.toast_product_deleted), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(getString(R.string.action_no), null)
                        .show();
                return true;
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvStock;
            VH(View v) {
                super(v);
                tvName  = v.findViewById(R.id.tvProductName);
                tvPrice = v.findViewById(R.id.tvProductPrice);
                tvStock = v.findViewById(R.id.tvProductStock);
            }
        }
    }
}