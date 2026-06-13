package com.wisedesign.africomptaplus.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.db.DatabaseHelper;
import java.io.*;

/**
 * Paramètres boutique : nom, téléphone, adresse, logo, pied de page facture.
 * Inclut : saisie code de licence + activation.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int PICK_LOGO = 101;
    private DatabaseHelper db;
    private EditText etShopName, etShopPhone, etShopAddress, etFooter, etLicenseKey;
    private TextView tvLogoPath, tvLicenseStatus;
    private String   selectedLogoPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        db = DatabaseHelper.getInstance(this);

        etShopName    = findViewById(R.id.etShopName);
        etShopPhone   = findViewById(R.id.etShopPhone);
        etShopAddress = findViewById(R.id.etShopAddress);
        etFooter      = findViewById(R.id.etInvoiceFooter);
        etLicenseKey  = findViewById(R.id.etLicenseKey);
        tvLogoPath    = findViewById(R.id.tvLogoPath);
        tvLicenseStatus = findViewById(R.id.tvLicenseStatus);

        loadConfig();

        findViewById(R.id.btnPickLogo).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(i, PICK_LOGO);
        });

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveConfig());

        findViewById(R.id.btnActivateLicense).setOnClickListener(v -> {
            String key = etLicenseKey.getText().toString().trim();
            if (key.isEmpty()) { Toast.makeText(this, "Saisissez votre clé", Toast.LENGTH_SHORT).show(); return; }
            activateLicense(key);
        });

        // Sauvegarde cloud (bouton info si pas de licence)
        findViewById(R.id.btnCloudBackup).setOnClickListener(v -> {
            com.wisedesign.africomptaplus.services.SecurityManager sm =
                    new com.wisedesign.africomptaplus.services.SecurityManager(this);
            if (!sm.isActivated()) {
                Toast.makeText(this, "Fonctionnalité Premium — Activez votre licence", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Sauvegarde cloud en cours de développement…", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadConfig() {
        etShopName.setText(db.getConfig("shop_name"));
        etShopPhone.setText(db.getConfig("shop_phone"));
        etShopAddress.setText(db.getConfig("shop_address"));
        etFooter.setText(db.getConfig("invoice_footer"));
        selectedLogoPath = db.getConfig("shop_logo_path");
        tvLogoPath.setText(selectedLogoPath.isEmpty() ? "Aucun logo sélectionné" : selectedLogoPath);

        com.wisedesign.africomptaplus.services.SecurityManager sm =
                new com.wisedesign.africomptaplus.services.SecurityManager(this);
        if (sm.isActivated()) {
            tvLicenseStatus.setText("✅ Licence active");
            tvLicenseStatus.setTextColor(0xFF43A047);
        } else {
            int days = sm.getRemainingDays();
            tvLicenseStatus.setText("⏳ Essai : " + days + " jour(s) restant(s)");
            tvLicenseStatus.setTextColor(0xFFE65100);
        }
    }

    private void saveConfig() {
        db.setConfig("shop_name",      etShopName.getText().toString().trim());
        db.setConfig("shop_phone",     etShopPhone.getText().toString().trim());
        db.setConfig("shop_address",   etShopAddress.getText().toString().trim());
        db.setConfig("invoice_footer", etFooter.getText().toString().trim());
        db.setConfig("shop_logo_path", selectedLogoPath);
        Toast.makeText(this, "Paramètres enregistrés", Toast.LENGTH_SHORT).show();
    }

    private void activateLicense(String key) {
        com.wisedesign.africomptaplus.services.SecurityManager sm =
                new com.wisedesign.africomptaplus.services.SecurityManager(this);
        String deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        Toast.makeText(this, "Vérification en cours…", Toast.LENGTH_SHORT).show();
        sm.verifyLicense(key, deviceId, (valid, message) -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (valid) loadConfig();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_LOGO && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                // Copier le logo dans le cache interne
                InputStream in = getContentResolver().openInputStream(uri);
                File logoFile  = new File(getFilesDir(), "shop_logo.jpg");
                FileOutputStream fos = new FileOutputStream(logoFile);
                byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                in.close(); fos.close();
                selectedLogoPath = logoFile.getAbsolutePath();
                tvLogoPath.setText(selectedLogoPath);
            } catch (Exception e) {
                Toast.makeText(this, "Erreur chargement logo", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
