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
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;
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
        UiKit.topBar(this, "Dettes");
        debtService  = new DebtService(this);
        BottomNav.attach(this, BottomNav.DEBTS);
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
        tvTotalDebts.setText(UiKit.xaf(total));
        ((TextView) findViewById(R.id.tvDebtCount)).setText(debts.size() + " dette(s)");
        findViewById(R.id.tvEmpty).setVisibility(debts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPaymentDialog(Debt debt) {
        EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint("Montant payé");
        new AlertDialog.Builder(this)
                .setTitle("Paiement — " + debt.clientName)
                .setMessage("Restant : " + UiKit.xaf(debt.remainingAmount()))
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
            h.tvInitials.setText(UiKit.initials(d.clientName));
            h.tvAmount.setText(UiKit.xaf(d.amount));
            h.tvRemaining.setText(UiKit.xaf(d.remainingAmount()));
            boolean partial = "partial".equals(d.status);
            h.tvStatus.setText(statusLabel(d.status));
            h.tvStatus.setBackgroundResource(partial ? R.drawable.bg_pill_info : R.drawable.bg_pill_warning);
            h.tvStatus.setTextColor(getResources().getColor(partial ? R.color.primary : R.color.accent));
            boolean hasDue = d.dueDate != null && !d.dueDate.isEmpty();
            h.rowDue.setVisibility(hasDue ? View.VISIBLE : View.GONE);
            if (hasDue) h.tvDue.setText("Échéance : " + d.dueDate);
            h.itemView.setOnClickListener(v -> showPaymentDialog(d));
            UiKit.pressScale(h.itemView);
        }
        @Override public int getItemCount() { return list.size(); }

        private String statusLabel(String s) {
            switch (s) { case "partial": return "Partiel"; case "paid": return "Payé"; default: return "En cours"; }
        }
        class VH extends RecyclerView.ViewHolder {
            TextView tvClient, tvAmount, tvRemaining, tvStatus, tvDue, tvInitials;
            View rowDue;
            VH(View v) {
                super(v);
                tvClient    = v.findViewById(R.id.tvDebtClient);
                tvAmount    = v.findViewById(R.id.tvDebtAmount);
                tvRemaining = v.findViewById(R.id.tvDebtRemaining);
                tvStatus    = v.findViewById(R.id.tvDebtStatus);
                tvDue       = v.findViewById(R.id.tvDebtDue);
                tvInitials  = v.findViewById(R.id.tvDebtInitials);
                rowDue      = v.findViewById(R.id.rowDue);
                tvInitials  = v.findViewById(R.id.tvDebtInitials);
                rowDue      = v.findViewById(R.id.rowDue);
            }
        }
    }
}
