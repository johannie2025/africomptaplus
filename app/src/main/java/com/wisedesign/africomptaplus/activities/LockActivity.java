package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.R;

/**
 * LockActivity — Écran de verrouillage non-dismissible.
 * Affiché si la période d'essai est expirée ou si une fraude d'horloge est détectée.
 * Redirige vers WhatsApp avec un message pré-rempli pour demander le code d'activation.
 */
public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_LOCK_REASON = "lock_reason";

    // WhatsApp Wise Design / Prophète Josias
    private static final String WHATSAPP_PHONE   = "+240555445514";
    private static final String WHATSAPP_MESSAGE =
            "Bonjour Wise Design, je souhaite obtenir mon code d'activation pour AfriCompta+. "
            + "Merci de me contacter pour procéder à l'activation complète de l'application.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        // ── Raison du verrouillage ────────────────────────────────────────
        String reason = getIntent().getStringExtra(EXTRA_LOCK_REASON);
        TextView tvReason = findViewById(R.id.tvLockReason);
        if (tvReason != null && reason != null) {
            switch (reason) {
                case "CLOCK_FRAUD":
                    tvReason.setText("⚠️ Fraude détectée : modification de l'horloge système.");
                    break;
                case "TRIAL_EXPIRED":
                    tvReason.setText("Votre période d'évaluation de 14 jours est terminée.");
                    break;
                default:
                    tvReason.setText("Cette application est verrouillée.");
            }
        }

        // ── Bouton WhatsApp ───────────────────────────────────────────────
        Button btnWhatsApp = findViewById(R.id.btnActivate);
        if (btnWhatsApp != null) {
            btnWhatsApp.setOnClickListener(v -> openWhatsApp());
        }
    }

    /** Ouvre WhatsApp avec le message pré-rempli. */
    private void openWhatsApp() {
        try {
            String encodedMsg = Uri.encode(WHATSAPP_MESSAGE);
            String url = "https://wa.me/" + WHATSAPP_PHONE.replace("+", "") + "?text=" + encodedMsg;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            // WhatsApp non installé → ouvre le navigateur
            String encodedMsg = Uri.encode(WHATSAPP_MESSAGE);
            String url = "https://wa.me/" + WHATSAPP_PHONE.replace("+", "") + "?text=" + encodedMsg;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        }
    }

    /** Empêche le retour arrière — l'écran est non-dismissible. */
    @Override
    public void onBackPressed() {
        // Bloqué intentionnellement
    }
}
