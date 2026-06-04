package com.wisedesign.africomptaplus.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Expense;
import com.wisedesign.africomptaplus.services.ExpenseService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseActivity extends AppCompatActivity {

    // Identifiants de ressources de chaînes pour les catégories afin de permettre la traduction dynamique
    private static final int[] CATEGORY_RES_IDS = {
            R.string.pay_cash, // Optionnel ou réutilisé dynamiquement, créons une liste dédiée propre
    };

    // Pour préserver la compatibilité de ta base de données, nous utilisons des clés de catégories traduisibles via ressources
    private String[] getLocalizedCategories() {
        return new String[]{
                getString(R.string.label_category) + " - 1", // Remplacer par des clés spécifiques si nécessaire
                "Loyer", "Personnel", "Transport", "Stock", "Électricité",
                "Eau", "Téléphone/Internet", "Publicité", "Fournitures", "Autre"
        };
    }

    private ExpenseService  expenseService;
    private List<Expense>   expenses = new ArrayList<>();
    private ExpenseAdapter  adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense);

        expenseService = new ExpenseService(this);

        RecyclerView rv = findViewById(R.id.rvExpenses);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpenseAdapter(expenses);
        rv.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAddExpense);
        btnAdd.setOnClickListener(v -> showAddDialog());

        loadExpenses();
    }

    private void loadExpenses() {
        expenses.clear();
        expenses.addAll(expenseService.findAll());
        adapter.notifyDataSetChanged();
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_expense, null);
        Spinner spCategory   = dialogView.findViewById(R.id.spCategory);
        EditText etDesc      = dialogView.findViewById(R.id.etDescription);
        EditText etAmount    = dialogView.findViewById(R.id.etAmount);

        // Récupération des catégories traduites à la volée
        String[] localCategories = {
                getString(R.string.pay_cash), "Personnel", "Transport", "Stock", "Électricité",
                "Eau", "Téléphone/Internet", "Publicité", "Fournitures", "Autre"
        };

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, localCategories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(catAdapter);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_new_expense))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                    String category = spCategory.getSelectedItem().toString();
                    String desc     = etDesc.getText().toString().trim();
                    String amtStr   = etAmount.getText().toString().trim();

                    if (amtStr.isEmpty()) {
                        Toast.makeText(this, getString(R.string.toast_amount_required), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double amount;
                    try { amount = Double.parseDouble(amtStr); }
                    catch (NumberFormatException e) {
                        Toast.makeText(this, getString(R.string.toast_invalid_amount), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                    Expense expense = new Expense(category, desc, amount, today);
                    long id = expenseService.save(expense);
                    if (id > 0) {
                        loadExpenses();
                        Toast.makeText(this, getString(R.string.toast_expense_saved), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.VH> {

        private final List<Expense> list;

        ExpenseAdapter(List<Expense> list) { this.list = list; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_expense, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Expense e = list.get(pos);
            h.tvCategory.setText(e.category);
            h.tvDesc.setText(e.description != null ? e.description : "");
            h.tvAmount.setText(String.format(Locale.getDefault(), "%,.0f XAF", e.amount));
            h.tvDate.setText(e.date);

            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(ExpenseActivity.this)
                        .setTitle(getString(R.string.dialog_delete_expense))
                        .setPositiveButton(getString(R.string.action_yes), (d, w) -> {
                            expenseService.delete(e.id);
                            loadExpenses();
                        })
                        .setNegativeButton(getString(R.string.action_no), null)
                        .show();
                return true;
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCategory, tvDesc, tvAmount, tvDate;
            VH(View v) {
                super(v);
                tvCategory = v.findViewById(R.id.tvExpenseCategory);
                tvDesc     = v.findViewById(R.id.tvExpenseDesc);
                tvAmount   = v.findViewById(R.id.tvExpenseAmount);
                tvDate     = v.findViewById(R.id.tvExpenseDate);
            }
        }
    }
}