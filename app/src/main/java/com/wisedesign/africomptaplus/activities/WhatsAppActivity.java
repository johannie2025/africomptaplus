package com.wisedesign.africomptaplus.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.services.BotService;
import com.wisedesign.africomptaplus.ui.UiKit;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/** Lien du numéro WhatsApp (QR), réglages du bot et synchronisation stock / ventes. */
public class WhatsAppActivity extends AppCompatActivity {
    private BotService bot;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean polling = false;
    private int ticks = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_whatsapp);
        UiKit.topBar(this, "Bot WhatsApp");
        bot = new BotService(this);
        findViewById(R.id.btnWaLink).setOnClickListener(v -> link());
        findViewById(R.id.btnWaUnlink).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Déconnecter WhatsApp ?").setMessage("Le bot cessera de répondre jusqu'à un nouveau scan.")
                .setPositiveButton("Déconnecter", (d, w) -> bot.logout((ok, msg, r) -> { toast(ok ? "Déconnecté" : msg); render(); }))
                .setNegativeButton("Annuler", null).show());
        findViewById(R.id.btnBotSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btnBotSync).setOnClickListener(v -> sync());
        UiKit.pressScale(findViewById(R.id.btnWaLink), findViewById(R.id.btnBotSave), findViewById(R.id.btnBotSync));
        ((SwitchMaterial) findViewById(R.id.swBotAuto)).setChecked(bot.auto());
        ((CheckBox) findViewById(R.id.cbBotCredit)).setChecked(bot.allowCredit());
        ((EditText) findViewById(R.id.etBotMomo)).setText(bot.momo());
        ((EditText) findViewById(R.id.etBotExtra)).setText(bot.extra());
    }

    @Override protected void onResume() {
        super.onResume(); render();
        if (bot.enabled()) bot.status((ok, msg, r) -> render());
    }
    @Override protected void onPause() { super.onPause(); polling = false; h.removeCallbacksAndMessages(null); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void render() {
        JSONObject c = bot.config();
        boolean enabled = bot.enabled();
        TextView st = findViewById(R.id.tvWaState), info = findViewById(R.id.tvWaInfo);
        View link = findViewById(R.id.btnWaLink), unlink = findViewById(R.id.btnWaUnlink);
        findViewById(R.id.boxSettings).setVisibility(enabled ? View.VISIBLE : View.GONE);
        findViewById(R.id.boxSync).setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            st.setText("Bot WhatsApp non activé");
            info.setText("Cette fonction n'est pas incluse dans votre licence. Contactez le support pour l'activer.");
            link.setVisibility(View.GONE); unlink.setVisibility(View.GONE);
            return;
        }
        boolean open = bot.linked();
        st.setText(open ? "● Connecté" + (c.optString("wa_phone").isEmpty() ? "" : "  +" + c.optString("wa_phone")) : "○ Numéro non lié");
        st.setTextColor(getResources().getColor(open ? R.color.success : R.color.accent));
        info.setText(open ? "Le bot répond à vos clients, ajoute au panier, encaisse et envoie la facture PDF."
                : "Scannez le QR code : WhatsApp → Appareils connectés → Connecter un appareil.");
        link.setVisibility(open ? View.GONE : View.VISIBLE);
        unlink.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) { findViewById(R.id.ivQr).setVisibility(View.GONE); findViewById(R.id.tvPairing).setVisibility(View.GONE); polling = false; }
        long last = 0; try { last = Long.parseLong(com.wisedesign.africomptaplus.db.DatabaseHelper.getInstance(this).getConfig("bot_last_sync")); } catch (Exception ignored) {}
        ((TextView) findViewById(R.id.tvSyncInfo)).setText(last > 0
                ? "Dernière synchro : " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(last))
                : "Pas encore synchronisé : envoyez votre catalogue pour que le bot connaisse vos produits.");
    }

    private void link() {
        ((TextView) findViewById(R.id.tvWaLinkLabel)).setText("Génération du QR…");
        bot.connect((ok, msg, r) -> {
            ((TextView) findViewById(R.id.tvWaLinkLabel)).setText("Lier mon WhatsApp (QR code)");
            if (!ok || r == null) { toast(msg.isEmpty() ? "Échec" : msg); return; }
            if ("open".equals(r.optString("wa_state"))) { toast("Déjà connecté"); bot.status((o, m, x) -> render()); return; }
            showQr(r.optString("qr", ""), r.optString("pairing_code", ""));
            // première synchro dès que le numéro sera lié
            startPolling();
        });
    }

    private void showQr(String qr, String pairing) {
        ImageView iv = findViewById(R.id.ivQr);
        try {
            String b64 = qr.contains(",") ? qr.substring(qr.indexOf(',') + 1) : qr;
            byte[] raw = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bm != null) { iv.setImageBitmap(bm); iv.setVisibility(View.VISIBLE); }
        } catch (Exception ignored) {}
        TextView tp = findViewById(R.id.tvPairing);
        if (!pairing.isEmpty()) { tp.setText("Ou code de jumelage : " + pairing); tp.setVisibility(View.VISIBLE); }
    }

    private void startPolling() {
        if (polling) return;
        polling = true; ticks = 0;
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (!polling) return;
                ticks++;
                if (ticks > 50) { polling = false; return; }   // ~2,5 min
                bot.status((ok, msg, r) -> {
                    if (bot.linked()) { polling = false; render(); sync(); toast("WhatsApp lié ✓"); return; }
                    if (ticks % 8 == 0) bot.connect((o, m, x) -> { if (o && x != null && !x.optString("qr").isEmpty()) showQr(x.optString("qr"), x.optString("pairing_code", "")); });
                });
                h.postDelayed(this, 3000);
            }
        }, 3000);
    }

    private void saveSettings() {
        bot.saveSettings(((EditText) findViewById(R.id.etBotMomo)).getText().toString().trim(),
                ((EditText) findViewById(R.id.etBotExtra)).getText().toString().trim(),
                ((CheckBox) findViewById(R.id.cbBotCredit)).isChecked(),
                ((SwitchMaterial) findViewById(R.id.swBotAuto)).isChecked());
        sync();
    }

    private void sync() {
        bot.syncNow((ok, msg, r) -> { toast(ok ? msg : "Synchro impossible : " + msg); render(); });
    }
}
