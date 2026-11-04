package com.wisedesign.africomptaplus.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.models.SaleItem;
import com.wisedesign.africomptaplus.services.InvoiceManager;
import com.wisedesign.africomptaplus.services.SecurityManager;
import com.wisedesign.africomptaplus.ui.UiKit;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Ma boutique & licence : identité (logo, coordonnées, pied de page) → factures PDF ; activation licence. */
public class SettingsActivity extends AppCompatActivity {

    private static final int PICK_LOGO = 101;
    private DatabaseHelper db;
    private SecurityManager sm;
    private EditText etShopName, etTagline, etShopPhone, etEmail, etShopAddress, etTaxId, etFooter, etLicenseKey;
    private TextView tvLogoPath, tvLicenseStatus, tvLicenseInfo, tvDeviceId, btnRemoveLogo;
    private ImageView ivLogo;
    private String selectedLogoPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        UiKit.topBar(this, "Ma boutique");
        db = DatabaseHelper.getInstance(this);
        sm = new SecurityManager(this);

        etShopName    = findViewById(R.id.etShopName);
        etTagline     = findViewById(R.id.etShopTagline);
        etShopPhone   = findViewById(R.id.etShopPhone);
        etEmail       = findViewById(R.id.etShopEmail);
        etShopAddress = findViewById(R.id.etShopAddress);
        etTaxId       = findViewById(R.id.etShopTaxId);
        etFooter      = findViewById(R.id.etInvoiceFooter);
        etLicenseKey  = findViewById(R.id.etLicenseKey);
        tvLogoPath    = findViewById(R.id.tvLogoPath);
        tvLicenseStatus = findViewById(R.id.tvLicenseStatus);
        tvLicenseInfo = findViewById(R.id.tvLicenseInfo);
        tvDeviceId    = findViewById(R.id.tvDeviceId);
        btnRemoveLogo = findViewById(R.id.btnRemoveLogo);
        ivLogo        = findViewById(R.id.ivLogoPreview);

        loadConfig();

        findViewById(R.id.btnPickLogo).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Choisir un logo"), PICK_LOGO);
        });
        btnRemoveLogo.setOnClickListener(v -> { selectedLogoPath = ""; showLogo(); });

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveConfig());
        findViewById(R.id.btnPreviewInvoice).setOnClickListener(v -> previewInvoice());

        findViewById(R.id.btnActivateLicense).setOnClickListener(v -> {
            String key = etLicenseKey.getText().toString().trim();
            if (key.isEmpty()) { Toast.makeText(this, "Saisissez votre clé", Toast.LENGTH_SHORT).show(); return; }
            Toast.makeText(this, "Vérification en cours…", Toast.LENGTH_SHORT).show();
            sm.verifyLicense(key, sm.deviceId(), (valid, message) -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if (valid) { etLicenseKey.setText(""); loadConfig(); }
            });
        });

        findViewById(R.id.btnCloudBackup).setOnClickListener(v -> {
            if (!sm.isActivated()) Toast.makeText(this, "Fonctionnalité Premium — activez votre licence", Toast.LENGTH_LONG).show();
            else Toast.makeText(this, "Sauvegarde cloud : bientôt disponible", Toast.LENGTH_SHORT).show();
        });
        UiKit.pressScale(findViewById(R.id.btnCloudBackup));
    }

    private void loadConfig() {
        etShopName.setText(db.getConfig("shop_name"));
        etTagline.setText(db.getConfig("shop_tagline"));
        etShopPhone.setText(db.getConfig("shop_phone"));
        etEmail.setText(db.getConfig("shop_email"));
        etShopAddress.setText(db.getConfig("shop_address"));
        etTaxId.setText(db.getConfig("shop_tax_id"));
        etFooter.setText(db.getConfig("invoice_footer"));
        selectedLogoPath = UiKit.logoPath(this);
        showLogo();

        tvDeviceId.setText("Identifiant appareil : " + sm.deviceId());
        if (sm.isActivated()) {
            tvLicenseStatus.setText("Active");
            tvLicenseStatus.setBackgroundResource(R.drawable.bg_pill_success);
            tvLicenseStatus.setTextColor(getResources().getColor(R.color.success));
            String plan = db.getConfig("license_plan"), exp = db.getConfig("license_expires"), cust = db.getConfig("license_customer");
            StringBuilder sb = new StringBuilder();
            if (!cust.isEmpty()) sb.append("Titulaire : ").append(cust).append("\n");
            if (!plan.isEmpty()) sb.append("Offre : ").append(plan).append("\n");
            sb.append("Validité : ").append(exp.isEmpty() ? "illimitée" : exp);
            tvLicenseInfo.setText(sb.toString());
            tvLicenseInfo.setVisibility(View.VISIBLE);
        } else {
            tvLicenseStatus.setText("Essai : " + sm.getRemainingDays() + " j");
            tvLicenseStatus.setBackgroundResource(R.drawable.bg_pill_warning);
            tvLicenseStatus.setTextColor(getResources().getColor(R.color.accent));
            tvLicenseInfo.setVisibility(View.GONE);
        }
    }

    private void showLogo() {
        if (selectedLogoPath.isEmpty() || !new File(selectedLogoPath).exists()) {
            ivLogo.setImageResource(R.drawable.ic_image);
            ivLogo.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.text_hint)));
            tvLogoPath.setText("Aucun logo (PNG/JPG, fond transparent conseillé)");
            btnRemoveLogo.setVisibility(View.GONE);
        } else {
            Bitmap b = BitmapFactory.decodeFile(selectedLogoPath);
            ivLogo.setImageTintList(null);
            ivLogo.setImageBitmap(b);
            tvLogoPath.setText("Logo prêt — apparaît sur vos factures");
            btnRemoveLogo.setVisibility(View.VISIBLE);
        }
    }

    private void saveConfig() {
        String name = etShopName.getText().toString().trim();
        if (name.isEmpty()) { etShopName.setError("Nom requis"); return; }
        db.setConfig("shop_name",      name);
        db.setConfig("shop_tagline",   etTagline.getText().toString().trim());
        db.setConfig("shop_phone",     etShopPhone.getText().toString().trim());
        db.setConfig("shop_email",     etEmail.getText().toString().trim());
        db.setConfig("shop_address",   etShopAddress.getText().toString().trim());
        db.setConfig("shop_tax_id",    etTaxId.getText().toString().trim());
        db.setConfig("invoice_footer", etFooter.getText().toString().trim());
        db.setConfig("shop_logo_path", selectedLogoPath);
        Toast.makeText(this, "Paramètres enregistrés", Toast.LENGTH_SHORT).show();
    }

    /** Génère une facture d'exemple avec l'identité saisie (enregistre d'abord). */
    private void previewInvoice() {
        if (etShopName.getText().toString().trim().isEmpty()) { etShopName.setError("Nom requis"); return; }
        saveConfig();
        Sale s = new Sale();
        s.invoiceNumber = "APERCU-0001";
        s.clientName = "Awa Koné"; s.clientPhone = "+225 07 08 12 34 56";
        s.paymentMethod = "cash";
        s.createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date());
        List<SaleItem> items = new ArrayList<>();
        items.add(new SaleItem(0, "Écouteurs sans fil Pro", 0, 1, 8500));
        items.add(new SaleItem(0, "Power Bank 20 000 mAh", 0, 2, 11000));
        items.add(new SaleItem(0, "Câble USB-C 1 m", 0, 1, 2250));
        double t = 0; for (SaleItem i : items) t += i.totalPrice;
        s.totalAmount = t; s.items = items;
        Uri uri = new InvoiceManager(this).generateInvoicePDF(s, items);
        if (uri == null) { Toast.makeText(this, "Erreur de génération", Toast.LENGTH_SHORT).show(); return; }
        Intent v = new Intent(Intent.ACTION_VIEW);
        v.setDataAndType(uri, "application/pdf");
        v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(v); }
        catch (Exception e) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Aperçu de la facture"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_LOGO || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (o.outWidth / sample > 1024 || o.outHeight / sample > 1024) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, o2); }
            if (bmp == null) throw new IOException("decode");
            File f = new File(getFilesDir(), "shop_logo.png");   // PNG : conserve la transparence
            try (FileOutputStream fos = new FileOutputStream(f)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, fos); }
            selectedLogoPath = f.getAbsolutePath();
            showLogo();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur chargement logo", Toast.LENGTH_SHORT).show();
        }
    }
}
