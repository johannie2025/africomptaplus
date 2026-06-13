package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Debt;
import com.wisedesign.africomptaplus.services.DebtService;
import java.util.*;

public class DebtActivity extends AppCompatActivity {

    private DebtService   debtService;
    private List<Debt>    debts = new ArrayList<>();
    private DebtAdapter   adapter;
    private TextView      tvTotalDebts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debt);
        debtService  = new DebtService(this);
        tvTotalDebts = findViewById(R.id.tvTotalDebts);
        RecyclerView rv = findViewById(R.id.rvDebts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DebtAdapter(debts);
        rv.setAdapter(adapter);
        loadDebts();
    }

    @Override protected void onResume() { super.onResume(); loadDebts(); }

    private void loadDebts() {
        debts.clear();
        debts.addAll(debtService.findAllOpen());
        adapter.notifyDataSetChanged();
        double total = debtService.totalOutstanding();
        tvTotalDebts.setText(String.format(java.util.Locale.FRENCH, "Total dû : %,.0f XAF", total));
    }

    private void showPaymentDialog(Debt debt) {
        EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint("Montant payé");
        new AlertDialog.Builder(this)
                .setTitle("Paiement — " + debt.clientName)
                .setMessage("Restant : " + String.format(java.util.Locale.FRENCH, "%,.0f XAF", debt.remainingAmount()))
                .setView(et)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String v = et.getText().toString().trim();
                    if (v.isEmpty()) return;
                    debtService.recordPayment(debt.id, Double.parseDouble(v));
                    loadDebts();
                    Toast.makeText(this, "Paiement enregistré", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null).show();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private class DebtAdapter extends RecyclerView.Adapter<DebtAdapter.VH> {
        private final List<Debt> list;
        DebtAdapter(List<Debt> l) { list = l; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_debt, p, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Debt d = list.get(pos);
            h.tvClient.setText(d.clientName != null ? d.clientName : "—");
            h.tvAmount.setText(String.format(java.util.Locale.FRENCH, "%,.0f XAF", d.amount));
            h.tvRemaining.setText(String.format(java.util.Locale.FRENCH, "Restant : %,.0f XAF", d.remainingAmount()));
            h.tvStatus.setText(statusLabel(d.status));
            h.tvStatus.setTextColor(d.status.equals("open") ? 0xFFE53935 : 0xFFFF6F00);
            h.tvDue.setText(d.dueDate != null && !d.dueDate.isEmpty() ? "Échéance : " + d.dueDate : "");
            h.itemView.setOnClickListener(v -> showPaymentDialog(d));
        }
        @Override public int getItemCount() { return list.size(); }

        private String statusLabel(String s) {
            switch (s) { case "partial": return "Partiel"; case "paid": return "Payé"; default: return "En cours"; }
        }
        class VH extends RecyclerView.ViewHolder {
            TextView tvClient, tvAmount, tvRemaining, tvStatus, tvDue;
            VH(View v) {
                super(v);
                tvClient    = v.findViewById(R.id.tvDebtClient);
                tvAmount    = v.findViewById(R.id.tvDebtAmount);
                tvRemaining = v.findViewById(R.id.tvDebtRemaining);
                tvStatus    = v.findViewById(R.id.tvDebtStatus);
                tvDue       = v.findViewById(R.id.tvDebtDue);
            }
        }
    }
}
