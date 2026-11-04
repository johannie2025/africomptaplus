package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.services.SecurityManager;

/** Écran de verrouillage : saisie directe de la clé de licence (API) ou demande WhatsApp. */
public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_LOCK_REASON = "lock_reason";
    private static final String WHATSAPP_PHONE = "+240555445514";
    private static final String WHATSAPP_MESSAGE =
            "Bonjour Wise Design, je souhaite obtenir ma clé de licence pour AfriCompta+. "
            + "Merci de me contacter pour procéder à l'activation.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        String reason = getIntent().getStringExtra(EXTRA_LOCK_REASON);
        TextView tvReason = findViewById(R.id.tvLockReason);
        if (reason != null) {
            switch (reason) {
                case "CLOCK_FRAUD": tvReason.setText("Modification de l'horloge système détectée."); break;
                case "TRIAL_EXPIRED": tvReason.setText("Votre période d'évaluation de 14 jours est terminée."); break;
                default: tvReason.setText("Cette application est verrouillée. Saisissez une clé valide.");
            }
        }

        EditText etKey = findViewById(R.id.etLockKey);
        TextView tvStatus = findViewById(R.id.tvLockStatus);
        Button btn = findViewById(R.id.btnLockActivate);
        SecurityManager sm = new SecurityManager(this);

        btn.setOnClickListener(v -> {
            String key = etKey.getText().toString().trim();
            if (key.isEmpty()) { etKey.setError("Clé requise"); return; }
            btn.setEnabled(false); btn.setText("Vérification…");
            tvStatus.setVisibility(View.GONE);
            sm.verifyLicense(key, sm.deviceId(), (valid, message) -> {
                btn.setEnabled(true); btn.setText("Activer");
                if (valid) {
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                } else {
                    tvStatus.setText(message);
                    tvStatus.setVisibility(View.VISIBLE);
                }
            });
        });

        findViewById(R.id.btnActivate).setOnClickListener(v -> openWhatsApp());
    }

    private void openWhatsApp() {
        String url = "https://wa.me/" + WHATSAPP_PHONE.replace("+", "") + "?text=" + Uri.encode(WHATSAPP_MESSAGE);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    @Override public void onBackPressed() { /* écran non-dismissible */ }
}
