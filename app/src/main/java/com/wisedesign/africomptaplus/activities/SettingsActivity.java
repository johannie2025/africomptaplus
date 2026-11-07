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
    private com.google.android.material.switchmaterial.SwitchMaterial swTva;
    private EditText etTvaRate;
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

        swTva     = findViewById(R.id.swTva);
        etTvaRate = findViewById(R.id.etTvaRate);
        swTva.setOnCheckedChangeListener((b, on) ->
                findViewById(R.id.boxTvaRate).setVisibility(on ? View.VISIBLE : View.GONE));
        findViewById(R.id.btnSaveTva).setOnClickListener(v -> saveTva());

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

        setupBackup();
        setupAccounting();
        setupCountry();
    }

    private void setupCountry() {
        Spinner sp = findViewById(R.id.spCountry);
        java.util.List<com.wisedesign.africomptaplus.services.Countries.Country> list = com.wisedesign.africomptaplus.services.Countries.all();
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, list));
        com.wisedesign.africomptaplus.services.Countries.Country cur = com.wisedesign.africomptaplus.services.Countries.byCode(db.getConfig("shop_country"));
        if (cur != null) sp.setSelection(list.indexOf(cur));
        TextView info = findViewById(R.id.tvCountryInfo);
        info.setText(cur == null ? "Aucun pays chargé" : "Chargé : " + cur.name + " — " + cur.currency + ", TVA " + com.wisedesign.africomptaplus.services.Countries.vatText(cur.vat) + " %");
        findViewById(R.id.btnLoadCountry).setOnClickListener(v -> {
            com.wisedesign.africomptaplus.services.Countries.Country c = (com.wisedesign.africomptaplus.services.Countries.Country) sp.getSelectedItem();
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Charger " + c.name + " ?")
                .setMessage(com.wisedesign.africomptaplus.services.Countries.summary(c)
                        + "\n\nLe taux de TVA est indicatif : vérifiez-le auprès de votre administration fiscale. Vos données ne sont pas modifiées.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Charger", (d, w) -> {
                    com.wisedesign.africomptaplus.services.Countries.apply(this, c);
                    Toast.makeText(this, "Configuration " + c.name + " chargée", Toast.LENGTH_LONG).show();
                    recreate();
                }).show();
        });
        UiKit.pressScale(findViewById(R.id.btnLoadCountry));
    }

    private void setupAccounting() {
        RadioGroup rg = findViewById(R.id.rgAcct);
        String m = db.getConfig("acct_mode");
        rg.check("simple".equals(m) ? R.id.rbAcctSimple : "double".equals(m) ? R.id.rbAcctDouble : R.id.rbAcctOff);
        rg.setOnCheckedChangeListener((g, id) -> {
            String v = id == R.id.rbAcctSimple ? "simple" : id == R.id.rbAcctDouble ? "double" : "off";
            db.setConfig("acct_mode", v);
            Toast.makeText(this, "Comptabilité : " + ("off".equals(v) ? "désactivée" : "simple".equals(v) ? "simple" : "double (OHADA)"), Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnOpenAccounting).setOnClickListener(v -> {
            if ("off".equals(db.getConfig("acct_mode")) || db.getConfig("acct_mode").isEmpty())
                Toast.makeText(this, "Choisissez d'abord un mode de comptabilité", Toast.LENGTH_SHORT).show();
            else startActivity(new Intent(this, AccountingActivity.class));
        });
        UiKit.pressScale(findViewById(R.id.btnOpenAccounting));
    }

    // ── Sauvegarde / restauration ───────────────────────────────────────────
    private com.wisedesign.africomptaplus.services.BackupManager backup;
    private Runnable pendingDriveAction;
    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest> authLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(), res -> {
            try {
                com.google.android.gms.auth.api.identity.AuthorizationResult r =
                    com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(res.getData());
                if (r.getAccessToken() != null && pendingDriveAction != null) runDrive(r.getAccessToken());
            } catch (Exception e) { gmsError(); }
        });
    private final androidx.activity.result.ActivityResultLauncher<String[]> importLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                confirmRestore(bo.toByteArray());
            } catch (Exception e) { toast("Fichier illisible"); }
        });
    private boolean driveRestoreMode;

    private void setupBackup() {
        backup = new com.wisedesign.africomptaplus.services.BackupManager(this);
        showLastBackup();
        int[] ids = {R.id.btnDriveBackup, R.id.btnDriveRestore, R.id.btnFileExport, R.id.btnFileImport};
        for (int id : ids) UiKit.pressScale(findViewById(id));

        findViewById(R.id.btnFileExport).setOnClickListener(v -> {
            if (!premium()) return;
            try {
                Uri u = backup.writeBackupFile();
                Intent i = new Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                        .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Enregistrer la sauvegarde"));
                markBackup();
            } catch (Exception e) { toast("Échec : " + e.getMessage()); }
        });
        findViewById(R.id.btnFileImport).setOnClickListener(v -> {
            if (premium()) importLauncher.launch(new String[]{"*/*"});
        });
        findViewById(R.id.btnDriveBackup).setOnClickListener(v -> { if (premium()) startDrive(false); });
        findViewById(R.id.btnDriveRestore).setOnClickListener(v -> { if (premium()) startDrive(true); });
    }

    private boolean premium() {
        if (sm.isActivated()) return true;
        toast("Fonctionnalité Premium — activez votre licence");
        return false;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }
    private void gmsError() { toast("Services Google indisponibles — utilisez la sauvegarde par fichier"); }

    private void startDrive(boolean restore) {
        driveRestoreMode = restore;
        pendingDriveAction = () -> {};
        try {
            com.google.android.gms.auth.api.identity.AuthorizationRequest req =
                com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
                    .setRequestedScopes(java.util.Collections.singletonList(
                        new com.google.android.gms.common.api.Scope(
                            com.wisedesign.africomptaplus.services.DriveBackup.SCOPE))).build();
            com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).authorize(req)
                .addOnSuccessListener(r -> {
                    if (r.hasResolution() && r.getPendingIntent() != null) {
                        authLauncher.launch(new androidx.activity.result.IntentSenderRequest
                                .Builder(r.getPendingIntent().getIntentSender()).build());
                    } else if (r.getAccessToken() != null) runDrive(r.getAccessToken());
                    else gmsError();
                })
                .addOnFailureListener(e -> gmsError());
        } catch (Throwable t) { gmsError(); }
    }

    private void runDrive(String token) {
        toast(driveRestoreMode ? "Recherche de la sauvegarde…" : "Envoi vers Drive…");
        new Thread(() -> {
            try {
                if (!driveRestoreMode) {
                    byte[] data = backup.createBackup();
                    String name = "AfriCompta_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                            .format(new java.util.Date()) + com.wisedesign.africomptaplus.services.BackupManager.EXT;
                    com.wisedesign.africomptaplus.services.DriveBackup.upload(token, name, data);
                    runOnUiThread(() -> { markBackup(); toast("Sauvegarde Drive terminée ✓"); });
                } else {
                    org.json.JSONArray l = com.wisedesign.africomptaplus.services.DriveBackup.list(token);
                    if (l.length() == 0) { runOnUiThread(() -> toast("Aucune sauvegarde sur Drive")); return; }
                    byte[] data = com.wisedesign.africomptaplus.services.DriveBackup
                            .download(token, l.getJSONObject(0).getString("id"));
                    runOnUiThread(() -> confirmRestore(data));
                }
            } catch (Exception e) { runOnUiThread(() -> toast("Échec : " + e.getMessage())); }
        }).start();
    }

    private void confirmRestore(byte[] data) {
        String info;
        try { info = backup.describe(data); } catch (Exception e) { toast("Sauvegarde invalide"); return; }
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Restaurer ?")
            .setMessage(info + "\n\nLes données actuelles seront remplacées.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Restaurer", (d, w) -> {
                try { backup.restore(data); toast("Restauration terminée ✓"); recreate(); }
                catch (Exception e) { toast("Échec : " + e.getMessage()); }
            }).show();
    }

    private void markBackup() {
        db.setConfig("cloud_last_backup", new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
                .format(new java.util.Date()));
        showLastBackup();
    }

    private void showLastBackup() {
        String t = db.getConfig("cloud_last_backup");
        ((TextView) findViewById(R.id.tvLastBackup)).setText(
            (t == null || t.isEmpty()) ? "Aucune sauvegarde effectuée" : "Dernière sauvegarde : " + t);
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
        boolean tva = "1".equals(db.getConfig("tva_enabled"));
        swTva.setChecked(tva);
        String r = db.getConfig("tva_rate");
        etTvaRate.setText(r.isEmpty() ? "18" : r);
        findViewById(R.id.boxTvaRate).setVisibility(tva ? View.VISIBLE : View.GONE);

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

    private void saveTva() {
        boolean on = swTva.isChecked();
        String raw = etTvaRate.getText().toString().trim().replace(',', '.');
        if (on) {
            double r;
            try { r = Double.parseDouble(raw); } catch (Exception e) { etTvaRate.setError("Taux invalide"); return; }
            if (r <= 0 || r > 100) { etTvaRate.setError("Entre 0 et 100"); return; }
            db.setConfig("tva_rate", raw);
        }
        db.setConfig("tva_enabled", on ? "1" : "0");
        Toast.makeText(this, on ? "TVA activée" : "TVA désactivée", Toast.LENGTH_SHORT).show();
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
        s.subtotal = t;
        s.taxRate = new com.wisedesign.africomptaplus.services.SaleService(this).taxRate();
        s.taxAmount = Math.round(t * s.taxRate / 100.0);
        s.totalAmount = t + s.taxAmount; s.items = items;
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
