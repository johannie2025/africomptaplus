package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Client;
import com.wisedesign.africomptaplus.models.Debt;
import com.wisedesign.africomptaplus.services.ClientService;
import com.wisedesign.africomptaplus.services.DebtService;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;
import java.util.*;

public class ClientListActivity extends AppCompatActivity {

    private ClientService clientService;
    private DebtService   debtService;
    private final List<Client> all = new ArrayList<>();
    private final List<Client> shown = new ArrayList<>();
    private final Map<Long, Double> balances = new HashMap<>();
    private ClientAdapter adapter;
    private EditText etSearch;
    private TextView chipAll, chipDebtors, chipClear, tvEmpty;
    private int filter = 0; // 0 tous, 1 débiteurs, 2 à jour

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_list);
        UiKit.topBar(this, "Clients");
        UiKit.topAction(this, "Nouveau", R.drawable.ic_plus, v -> showClientForm(null));
        clientService = new ClientService(this);
        debtService   = new DebtService(this);

        RecyclerView rv = findViewById(R.id.rvClients);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ClientAdapter(shown);
        rv.setAdapter(adapter);

        etSearch    = findViewById(R.id.etSearchClient);
        chipAll     = findViewById(R.id.chipAll);
        chipDebtors = findViewById(R.id.chipDebtors);
        chipClear   = findViewById(R.id.chipClear);
        tvEmpty     = findViewById(R.id.tvEmpty);
        chipAll.setOnClickListener(v -> setFilter(0));
        chipDebtors.setOnClickListener(v -> setFilter(1));
        chipClear.setOnClickListener(v -> setFilter(2));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        BottomNav.attach(this, BottomNav.CLIENTS);
    }

    @Override protected void onResume() { super.onResume(); loadClients(); }

    private void setFilter(int f) {
        filter = f;
        chipAll.setSelected(f == 0); chipDebtors.setSelected(f == 1); chipClear.setSelected(f == 2);
        applyFilter();
    }

    private void loadClients() {
        all.clear(); all.addAll(clientService.findAll());
        balances.clear();
        int debtors = 0;
        for (Client c : all) {
            double t = 0;
            for (Debt d : debtService.findByClient(c.id)) t += d.remainingAmount();
            balances.put(c.id, t);
            if (t > 0) debtors++;
        }
        ((TextView) findViewById(R.id.tvStatTotal)).setText(String.valueOf(all.size()));
        ((TextView) findViewById(R.id.tvStatDebtors)).setText(String.valueOf(debtors));
        ((TextView) findViewById(R.id.tvStatClear)).setText(String.valueOf(all.size() - debtors));
        setFilter(filter);
    }

    private void applyFilter() {
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Client c : all) {
            double bal = balances.containsKey(c.id) ? balances.get(c.id) : 0;
            if (filter == 1 && bal <= 0) continue;
            if (filter == 2 && bal > 0) continue;
            if (!q.isEmpty()) {
                String hay = (c.name + " " + (c.phone == null ? "" : c.phone)).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            shown.add(c);
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showClientForm(Client existing) {
        View v = getLayoutInflater().inflate(R.layout.dialog_client_form, null);
        EditText etName    = v.findViewById(R.id.etClientName);
        EditText etPhone   = v.findViewById(R.id.etClientPhone);
        EditText etEmail   = v.findViewById(R.id.etClientEmail);
        EditText etAddress = v.findViewById(R.id.etClientAddress);
        if (existing != null) {
            etName.setText(existing.name); etPhone.setText(existing.phone);
            etEmail.setText(existing.email); etAddress.setText(existing.address);
        }
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nouveau client" : "Modifier " + existing.name)
                .setView(v)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show(); return; }
                    Client c = existing != null ? existing : new Client();
                    c.name = name;
                    c.phone = etPhone.getText().toString().trim();
                    c.email = etEmail.getText().toString().trim();
                    c.address = etAddress.getText().toString().trim();
                    clientService.save(c);
                    loadClients();
                    Toast.makeText(this, "Client enregistré", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null).show();
    }

    private class ClientAdapter extends RecyclerView.Adapter<ClientAdapter.VH> {
        private final List<Client> list;
        ClientAdapter(List<Client> l) { list = l; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_client, p, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Client c = list.get(pos);
            double total = balances.containsKey(c.id) ? balances.get(c.id) : 0;
            h.tvInitials.setText(UiKit.initials(c.name));
            h.tvName.setText(c.name);
            h.tvPhone.setText(c.phone != null ? c.phone : "");
            boolean owes = total > 0;
            h.tvStatus.setText(owes ? "Doit" : "Aucune dette");
            h.tvStatus.setBackgroundResource(owes ? R.drawable.bg_pill_danger : R.drawable.bg_pill_success);
            h.tvStatus.setTextColor(getResources().getColor(owes ? R.color.error : R.color.success));
            h.tvDebt.setText(owes ? UiKit.xaf(total) : UiKit.xaf(0));
            h.tvDebt.setTextColor(getResources().getColor(owes ? R.color.error : R.color.text_primary));
            h.itemView.setOnClickListener(v -> showClientForm(c));
            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(ClientListActivity.this)
                        .setTitle("Supprimer " + c.name + " ?")
                        .setPositiveButton("Oui", (d, w) -> { clientService.delete(c.id); loadClients(); })
                        .setNegativeButton("Non", null).show();
                return true;
            });
            UiKit.pressScale(h.itemView);
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvInitials, tvName, tvPhone, tvStatus, tvDebt;
            VH(View v) {
                super(v);
                tvInitials = v.findViewById(R.id.tvClientInitials);
                tvName   = v.findViewById(R.id.tvClientName);
                tvPhone  = v.findViewById(R.id.tvClientPhone);
                tvStatus = v.findViewById(R.id.tvClientStatus);
                tvDebt   = v.findViewById(R.id.tvClientDebt);
            }
        }
    }
}
