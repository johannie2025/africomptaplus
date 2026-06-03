package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.services.SecurityManager;

/**
 * SplashActivity — Point d'entrée de l'application.
 * Vérifie la sécurité (time-bomb) avant de router vers MainActivity ou LockActivity.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Vérification sécurité (time-bomb 14 jours)
        SecurityManager security = new SecurityManager(this);
        SecurityManager.LockReason reason = security.checkAndUpdate();

        if (reason == SecurityManager.LockReason.NONE) {
            int daysLeft = security.getRemainingDays();
            TextView tvDays = findViewById(R.id.tvTrialDays);
            if (tvDays != null && daysLeft < 14) {
                tvDays.setText("Période d'essai : " + daysLeft + " jour(s) restant(s)");
            }
            // Démarrage normal après 1.5s
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }, 1500);
        } else {
            // Verrouillage immédiat
            Intent lockIntent = new Intent(this, LockActivity.class);
            lockIntent.putExtra(LockActivity.EXTRA_LOCK_REASON, reason.name());
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
            finish();
        }
    }
}
