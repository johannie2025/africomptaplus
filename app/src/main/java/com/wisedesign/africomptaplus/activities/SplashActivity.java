package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.services.SecurityManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        SecurityManager security = new SecurityManager(this);
        SecurityManager.LockReason reason = security.checkAndUpdate();

        if (reason == SecurityManager.LockReason.NONE) {
            int daysLeft = security.getRemainingDays();
            TextView tvDays = findViewById(R.id.tvTrialDays);
            if (tvDays != null && daysLeft < 14) {
                tvDays.setText(getString(R.string.badge_trial_days_left, daysLeft));
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }, 1500);
        } else {
            Intent lockIntent = new Intent(this, LockActivity.class);
            lockIntent.putExtra(LockActivity.EXTRA_LOCK_REASON, reason.name());
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
            finish();
        }
    }
}