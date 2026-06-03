package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Product;
import com.wisedesign.africomptaplus.services.ProductService;

public class ProductFormActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";

    private ProductService productService;
    private Product currentProduct;

    private EditText etName, etBarcode, etBuyingPrice, etSellingPrice, etStock, etMinStock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);

        productService = new ProductService(this);

        etName         = findViewById(R.id.etName);
        etBarcode      = findViewById(R.id.etBarcode);
        etBuyingPrice  = findViewById(R.id.etBuyingPrice);
        etSellingPrice = findViewById(R.id.etSellingPrice);
        etStock        = findViewById(R.id.etStock);
        etMinStock     = findViewById(R.id.etMinStock);

        long productId = getIntent().getLongExtra(EXTRA_PRODUCT_ID, 0);
        if (productId > 0) {
            currentProduct = productService.findById(productId);
            if (currentProduct != null) populateForm(currentProduct);
        }

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveProduct());
    }

    private void populateForm(Product p) {
        etName.setText(p.name);
        etBarcode.setText(p.barcode != null ? p.barcode : "");
        etBuyingPrice.setText(String.valueOf(p.buyingPrice));
        etSellingPrice.setText(String.valueOf(p.sellingPrice));
        etStock.setText(String.valueOf(p.stock));
        etMinStock.setText(String.valueOf(p.minStock));
    }

    private void saveProduct() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Nom requis");
            return;
        }

        double buyingPrice, sellingPrice;
        int stock, minStock;
        try {
            buyingPrice  = Double.parseDouble(etBuyingPrice.getText().toString().trim());
            sellingPrice = Double.parseDouble(etSellingPrice.getText().toString().trim());
            stock        = Integer.parseInt(etStock.getText().toString().trim());
            minStock     = Integer.parseInt(etMinStock.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Veuillez saisir des valeurs numériques valides", Toast.LENGTH_SHORT).show();
            return;
        }

        Product p = currentProduct != null ? currentProduct : new Product();
        p.name         = name;
        p.barcode      = etBarcode.getText().toString().trim();
        p.buyingPrice  = buyingPrice;
        p.sellingPrice = sellingPrice;
        p.stock        = stock;
        p.minStock     = minStock;

        long id = productService.save(p);
        if (id > 0) {
            Toast.makeText(this, "Produit enregistré", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show();
        }
    }
}
