package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.R;

public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_LOCK_REASON = "lock_reason";

    private static final String WHATSAPP_PHONE   = "+240555445514";
    
    // Message de secours WhatsApp traduit dynamiquement selon la langue globale de l'appareil
    private String getLocalizedWhatsAppMessage() {
        return getString(R.string.trial_expired_body).replace("\n", " ") + " " + getString(R.string.footer_text);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        String reason = getIntent().getStringExtra(EXTRA_LOCK_REASON);
        TextView tvReason = findViewById(R.id.tvLockReason);
        if (tvReason != null && reason != null) {
            switch (reason) {
                case "CLOCK_FRAUD":
                    tvReason.setText(getString(R.string.lock_fraud));
                    break;
                case "TRIAL_EXPIRED":
                    tvReason.setText(getString(R.string.lock_expired));
                    break;
                default:
                    tvReason.setText(getString(R.string.lock_default));
            }
        }

        Button btnWhatsApp = findViewById(R.id.btnActivate);
        if (btnWhatsApp != null) {
            btnWhatsApp.setOnClickListener(v -> openWhatsApp());
        }
    }

    private void openWhatsApp() {
        String whatsappMsg = getLocalizedWhatsAppMessage();
        try {
            String encodedMsg = Uri.encode(whatsappMsg);
            String url = "https://wa.me/" + WHATSAPP_PHONE.replace("+", "") + "?text=" + encodedMsg;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            String encodedMsg = Uri.encode(whatsappMsg);
            String url = "https://wa.me/" + WHATSAPP_PHONE.replace("+", "") + "?text=" + encodedMsg;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        // Bloqué intentionnellement
    }
}