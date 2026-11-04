package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.wisedesign.africomptaplus.ui.Scanner;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Product;
import com.wisedesign.africomptaplus.services.ProductService;
import com.wisedesign.africomptaplus.ui.UiKit;

public class ProductFormActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";
    public static final String EXTRA_BARCODE = "barcode";

    private ProductService productService;
    private Product currentProduct;
    private EditText etName, etBarcode, etBuyingPrice, etSellingPrice, etStock, etMinStock;

    // Déclaré ICI pour que les variables ci-dessus soient déjà initialisées
    private final ActivityResultLauncher<ScanOptions> scanLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() == null) return;
                String code = result.getContents().trim();
                etBarcode.setText(code);
                Product other = productService.findByBarcode(code);
                if (other != null && (currentProduct == null || other.id != currentProduct.id)) {
                    etBarcode.setError("Déjà utilisé par : " + other.name);
                    Toast.makeText(this, "Ce code existe déjà : " + other.name, Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);

        productService = new ProductService(this);
        UiKit.topBar(this, getIntent().getLongExtra(EXTRA_PRODUCT_ID, 0) > 0 ? "Modifier le produit" : "Nouveau produit");

        etName         = findViewById(R.id.etName);
        etBarcode      = findViewById(R.id.etBarcode);
        etBuyingPrice  = findViewById(R.id.etBuyingPrice);
        etSellingPrice = findViewById(R.id.etSellingPrice);
        etStock        = findViewById(R.id.etStock);
        etMinStock     = findViewById(R.id.etMinStock);

        findViewById(R.id.btnScanBarcode).setOnClickListener(v -> 
            scanLauncher.launch(Scanner.options("Scannez le code-barres du produit"))
        );
        UiKit.pressScale(findViewById(R.id.btnScanBarcode));

        String preset = getIntent().getStringExtra(EXTRA_BARCODE);
        if (preset != null) etBarcode.setText(preset);

        long productId = getIntent().getLongExtra(EXTRA_PRODUCT_ID, 0);
        // ... le reste de votre logique onCreate
    }
}
