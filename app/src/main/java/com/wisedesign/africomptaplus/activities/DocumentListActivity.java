package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.Document;
import com.wisedesign.africomptaplus.models.Sale;
import com.wisedesign.africomptaplus.services.DocumentService;
import com.wisedesign.africomptaplus.services.InvoiceManager;
import com.wisedesign.africomptaplus.services.PdfPrinter;
import com.wisedesign.africomptaplus.services.SaleService;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.FilterBar;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/** Devis & factures proforma : recherche, filtre de période, PDF, impression, conversion en facture. */
public class DocumentListActivity extends AppCompatActivity {
    private DocumentService ds;
    private SaleService ss;
    private InvoiceManager im;
    private final List<Document> docs = new ArrayList<>();
    private Adapter adapter;
    private String type = "", q = "", from = "", to = "";
    private View[] tabs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_documents);
        UiKit.topBar(this, "Devis & Proforma");
        BottomNav.attach(this, BottomNav.NONE);
        ds = new DocumentService(this); ss = new SaleService(this); im = new InvoiceManager(this);

        RecyclerView rv = findViewById(R.id.rvDocs);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);

        tabs = new View[]{findViewById(R.id.tabDocAll), findViewById(R.id.tabDocQuote), findViewById(R.id.tabDocProforma)};
        final String[] types = {"", Document.QUOTE, Document.PROFORMA};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> { type = types[idx]; markTab(idx); load(); });
        }
        markTab(0);
        FilterBar.attach(this, (qq, f, t) -> { q = qq; from = f; to = t; load(); });
        load();
    }

    private void markTab(int idx) { for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(i == idx); }

    private void load() {
        docs.clear();
        docs.addAll(ds.search(q, type, from, to));
        adapter.notifyDataSetChanged();
        double total = 0;
        for (Document d : docs) total += d.totalAmount;
        ((TextView) findViewById(R.id.tvSummary)).setText(docs.size() + " document(s) — " + UiKit.xaf(total));
        findViewById(R.id.tvEmpty).setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Uri pdf(Document d) {
        Document full = ds.findById(d.id);
        return full == null ? null : im.generateDocumentPDF(full);
    }

    private void share(Uri u, String subject) {
        if (u == null) { Toast.makeText(this, "Erreur de génération du PDF", Toast.LENGTH_SHORT).show(); return; }
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("application/pdf");
        s.putExtra(Intent.EXTRA_STREAM, u);
        s.putExtra(Intent.EXTRA_SUBJECT, subject);
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s, "Partager via"));
    }

    private void actions(Document d) {
        List<String> labels = new ArrayList<>();
        labels.add("Partager le PDF"); labels.add("Imprimer");
        boolean open = "open".equals(d.status);
        if (open) labels.add("Convertir en facture");
        labels.add("Supprimer");
        new AlertDialog.Builder(this).setTitle(d.label() + " " + d.number)
                .setItems(labels.toArray(new String[0]), (dlg, w) -> {
                    String l = labels.get(w);
                    if (l.startsWith("Partager")) share(pdf(d), d.label() + " " + d.number);
                    else if (l.equals("Imprimer")) {
                        Uri u = pdf(d);
                        if (u != null) PdfPrinter.print(this, u, d.number);
                    }
                    else if (l.startsWith("Convertir")) chooseTender(d);
                    else confirmDelete(d);
                }).show();
    }

    private void chooseTender(Document d) {
        final String[] methods = {"cash", "mobile_money", "credit"};
        new AlertDialog.Builder(this).setTitle("Mode de paiement de la facture")
                .setItems(new String[]{"Espèces", "Mobile Money", "Crédit (dette client)"}, (dlg, w) -> {
                    String[] err = new String[1];
                    long saleId = ds.convert(d.id, methods[w], err);
                    if (saleId < 0) { Toast.makeText(this, err[0], Toast.LENGTH_LONG).show(); return; }
                    Sale sale = ss.findByIdWithItems(saleId);
                    Toast.makeText(this, "Facture créée — stock mis à jour", Toast.LENGTH_SHORT).show();
                    load();
                    if (sale != null) share(im.generateInvoicePDF(sale, sale.items), "Facture " + sale.invoiceNumber);
                }).show();
    }

    private void confirmDelete(Document d) {
        new AlertDialog.Builder(this).setTitle("Supprimer ?")
                .setMessage(d.number + " sera supprimé définitivement.")
                .setPositiveButton("Supprimer", (dlg, w) -> { ds.delete(d.id); load(); })
                .setNegativeButton("Annuler", null).show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_sale, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Document d = docs.get(pos);
            h.number.setText(d.number);
            h.client.setText(d.clientName);
            h.date.setText(d.createdAt + ("open".equals(d.status) && d.validUntil != null && !d.validUntil.isEmpty() ? "  ·  valable jusqu'au " + d.validUntil : ""));
            h.amount.setText(UiKit.xaf(d.totalAmount));
            h.tag.setText("converted".equals(d.status) ? "Facturé" : Document.PROFORMA.equals(d.docType) ? "Proforma" : "Devis");
            UiKit.pressScale(h.itemView);
            h.itemView.setOnClickListener(v -> actions(d));
        }
        @Override public int getItemCount() { return docs.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView number, client, date, amount, tag;
            VH(View v) {
                super(v);
                number = v.findViewById(R.id.tvSaleInvoice); client = v.findViewById(R.id.tvSaleClient);
                date = v.findViewById(R.id.tvSaleDate); amount = v.findViewById(R.id.tvSaleAmount);
                tag = v.findViewById(R.id.tvSalePayment);
            }
        }
    }
}
