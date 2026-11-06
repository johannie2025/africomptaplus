package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Debt;
import com.wisedesign.africomptaplus.models.DebtPayment;
import com.wisedesign.africomptaplus.services.DebtService;
import com.wisedesign.africomptaplus.services.PdfPrinter;
import com.wisedesign.africomptaplus.services.ReceiptManager;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/** Détail d'une dette : résumé, nouveau paiement, historique, reçu PDF (imprimer / partager). */
public class DebtDetailActivity extends AppCompatActivity {
    public static final String EXTRA_DEBT_ID = "debt_id";

    private DebtService debts;
    private ReceiptManager receipts;
    private long debtId;
    private final List<DebtPayment> payments = new ArrayList<>();
    private PayAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_debt_detail);
        UiKit.topBar(this, "Détail de la dette");
        BottomNav.attach(this, BottomNav.DEBTS);
        debts = new DebtService(this);
        receipts = new ReceiptManager(this);
        debtId = getIntent().getLongExtra(EXTRA_DEBT_ID, -1);

        RecyclerView rv = findViewById(R.id.rvPayments);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PayAdapter();
        rv.setAdapter(adapter);
        findViewById(R.id.btnAddPayment).setOnClickListener(v -> showPaymentDialog());
        UiKit.pressScale(findViewById(R.id.btnAddPayment));
        load();
    }

    private void load() {
        Debt d = debts.findById(debtId);
        if (d == null) { finish(); return; }
        ((TextView) findViewById(R.id.tvDDClient)).setText(d.clientName != null ? d.clientName : "—");
        ((TextView) findViewById(R.id.tvDDRef)).setText(d.notes != null ? d.notes : "");
        ((TextView) findViewById(R.id.tvDDTotal)).setText(UiKit.xaf(d.amount));
        ((TextView) findViewById(R.id.tvDDPaid)).setText(UiKit.xaf(d.paidAmount));
        ((TextView) findViewById(R.id.tvDDRemaining)).setText(UiKit.xaf(d.remainingAmount()));
        findViewById(R.id.btnAddPayment).setVisibility(d.isFullyPaid() ? View.GONE : View.VISIBLE);
        payments.clear();
        payments.addAll(debts.findPayments(debtId));
        adapter.notifyDataSetChanged();
        findViewById(R.id.tvEmptyPayments).setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPaymentDialog() {
        Debt d = debts.findById(debtId);
        if (d == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 8, pad, 0);

        EditText etAmount = new EditText(this);
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setHint("Montant payé");
        etAmount.setText(String.valueOf((long) d.remainingAmount()));
        etAmount.selectAll();
        box.addView(etAmount);

        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rCash = new RadioButton(this); rCash.setText("Espèces"); rCash.setId(View.generateViewId());
        RadioButton rMobile = new RadioButton(this); rMobile.setText("Mobile Money"); rMobile.setId(View.generateViewId());
        rg.addView(rCash); rg.addView(rMobile);
        rg.check(rCash.getId());
        box.addView(rg);

        EditText etNote = new EditText(this);
        etNote.setHint("Note (facultatif)");
        box.addView(etNote);

        new AlertDialog.Builder(this)
                .setTitle("Paiement — " + d.clientName)
                .setMessage("Restant : " + UiKit.xaf(d.remainingAmount()))
                .setView(box)
                .setPositiveButton("Enregistrer", (dlg, w) -> {
                    double amount;
                    try { amount = Double.parseDouble(etAmount.getText().toString().trim().replace(',', '.')); }
                    catch (Exception e) { Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show(); return; }
                    if (amount <= 0) { Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show(); return; }
                    String method = rg.getCheckedRadioButtonId() == rMobile.getId() ? "mobile_money" : "cash";
                    long pid = debts.recordPayment(debtId, amount, method, etNote.getText().toString().trim());
                    if (pid < 0) { Toast.makeText(this, "Paiement refusé", Toast.LENGTH_SHORT).show(); return; }
                    load();
                    offerReceipt(pid);
                })
                .setNegativeButton("Annuler", null).show();
    }

    private void offerReceipt(long paymentId) {
        new AlertDialog.Builder(this)
                .setTitle("Paiement enregistré ✓")
                .setMessage("Imprimer ou partager le reçu ?")
                .setPositiveButton("Imprimer", (d, w) -> receipt(paymentId, true))
                .setNeutralButton("Partager", (d, w) -> receipt(paymentId, false))
                .setNegativeButton("Plus tard", null).show();
    }

    private void receipt(long paymentId, boolean print) {
        Uri pdf = receipts.generate(paymentId);
        if (pdf == null) { Toast.makeText(this, "Erreur de génération du reçu", Toast.LENGTH_SHORT).show(); return; }
        if (print) { PdfPrinter.print(this, pdf, "Recu_REC_" + paymentId); return; }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, pdf);
        share.putExtra(Intent.EXTRA_SUBJECT, "Reçu de paiement");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Partager le reçu via"));
    }

    private class PayAdapter extends RecyclerView.Adapter<PayAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_payment, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            DebtPayment p = payments.get(pos);
            h.amount.setText(UiKit.xaf(p.amount));
            h.info.setText(("mobile_money".equals(p.method) ? "Mobile Money" : "Espèces")
                    + (p.note != null && !p.note.isEmpty() ? " · " + p.note : ""));
            h.date.setText(p.createdAt);
            UiKit.pressScale(h.itemView);
            h.itemView.setOnClickListener(v -> new AlertDialog.Builder(DebtDetailActivity.this)
                    .setTitle("Reçu REC-" + String.format(java.util.Locale.US, "%05d", p.id))
                    .setItems(new String[]{"Imprimer", "Partager le PDF"}, (d, w) -> receipt(p.id, w == 0))
                    .show());
        }
        @Override public int getItemCount() { return payments.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView amount, info, date;
            VH(View v) {
                super(v);
                amount = v.findViewById(R.id.tvPayAmount);
                info = v.findViewById(R.id.tvPayInfo);
                date = v.findViewById(R.id.tvPayDate);
            }
        }
    }
}
