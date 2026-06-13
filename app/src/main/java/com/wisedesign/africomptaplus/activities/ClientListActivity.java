package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Client;
import com.wisedesign.africomptaplus.services.ClientService;
import com.wisedesign.africomptaplus.services.DebtService;
import java.util.*;

public class ClientListActivity extends AppCompatActivity {

    private ClientService clientService;
    private DebtService   debtService;
    private List<Client>  clients = new ArrayList<>();
    private ClientAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_list);
        clientService = new ClientService(this);
        debtService   = new DebtService(this);
        RecyclerView rv = findViewById(R.id.rvClients);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ClientAdapter(clients);
        rv.setAdapter(adapter);
        com.google.android.material.floatingactionbutton.FloatingActionButton fab = findViewById(R.id.fabAddClient);
        if (fab != null) fab.setOnClickListener(v -> showClientForm(null));
    }

    @Override protected void onResume() { super.onResume(); loadClients(); }

    private void loadClients() {
        clients.clear();
        clients.addAll(clientService.findAll());
        adapter.notifyDataSetChanged();
    }

    private void showClientForm(Client existing) {
        View v = getLayoutInflater().inflate(R.layout.dialog_client_form, null);
        EditText etName    = v.findViewById(R.id.etClientName);
        EditText etPhone   = v.findViewById(R.id.etClientPhone);
        EditText etEmail   = v.findViewById(R.id.etClientEmail);
        EditText etAddress = v.findViewById(R.id.etClientAddress);
        if (existing != null) {
            etName.setText(existing.name);
            etPhone.setText(existing.phone);
            etEmail.setText(existing.email);
            etAddress.setText(existing.address);
        }
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nouveau client" : "Modifier " + existing.name)
                .setView(v)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show(); return; }
                    Client c = existing != null ? existing : new Client();
                    c.name    = name;
                    c.phone   = etPhone.getText().toString().trim();
                    c.email   = etEmail.getText().toString().trim();
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
            h.tvName.setText(c.name);
            h.tvPhone.setText(c.phone != null ? c.phone : "");
            // Solde dettes
            double total = 0;
            for (com.wisedesign.africomptaplus.models.Debt d : debtService.findByClient(c.id))
                total += d.remainingAmount();
            h.tvDebt.setText(total > 0 ? String.format(java.util.Locale.FRENCH, "Doit : %,.0f XAF", total) : "Aucune dette");
            h.tvDebt.setTextColor(total > 0 ? 0xFFE53935 : 0xFF43A047);
            h.itemView.setOnClickListener(v -> showClientForm(c));
            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(ClientListActivity.this)
                        .setTitle("Supprimer " + c.name + " ?")
                        .setPositiveButton("Oui", (d, w) -> { clientService.delete(c.id); loadClients(); })
                        .setNegativeButton("Non", null).show();
                return true;
            });
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPhone, tvDebt;
            VH(View v) {
                super(v);
                tvName  = v.findViewById(R.id.tvClientName);
                tvPhone = v.findViewById(R.id.tvClientPhone);
                tvDebt  = v.findViewById(R.id.tvClientDebt);
            }
        }
    }
}
