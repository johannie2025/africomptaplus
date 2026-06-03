package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.services.InvoiceManager;
import com.wisedesign.africomptaplus.services.SaleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SaleHistoryActivity extends AppCompatActivity {

    private SaleService    saleService;
    private InvoiceManager invoiceManager;
    private List<Sale>     sales = new ArrayList<>();
    private SaleAdapter    adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale_history);

        saleService    = new SaleService(this);
        invoiceManager = new InvoiceManager(this);

        RecyclerView rv = findViewById(R.id.rvSaleHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SaleAdapter(sales);
        rv.setAdapter(adapter);

        loadSales();
    }

    private void loadSales() {
        sales.clear();
        sales.addAll(saleService.findAll());
        adapter.notifyDataSetChanged();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class SaleAdapter extends RecyclerView.Adapter<SaleAdapter.VH> {

        private final List<Sale> list;

        SaleAdapter(List<Sale> list) { this.list = list; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sale, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Sale s = list.get(pos);
            h.tvInvoice.setText(s.invoiceNumber);
            h.tvAmount.setText(String.format(Locale.FRENCH, "%,.0f XAF", s.totalAmount));
            h.tvDate.setText(s.createdAt);
            h.tvPayment.setText(s.paymentMethod);

            // Re-générer et partager la facture PDF
            h.itemView.setOnClickListener(v -> {
                Sale fullSale = saleService.findByIdWithItems(s.id);
                if (fullSale != null) {
                    Uri pdfUri = invoiceManager.generateInvoicePDF(fullSale, fullSale.items);
                    if (pdfUri != null) {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("application/pdf");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent, "Partager la facture via"));
                    }
                }
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvInvoice, tvAmount, tvDate, tvPayment;
            VH(View v) {
                super(v);
                tvInvoice = v.findViewById(R.id.tvSaleInvoice);
                tvAmount  = v.findViewById(R.id.tvSaleAmount);
                tvDate    = v.findViewById(R.id.tvSaleDate);
                tvPayment = v.findViewById(R.id.tvSalePayment);
            }
        }
    }
}
