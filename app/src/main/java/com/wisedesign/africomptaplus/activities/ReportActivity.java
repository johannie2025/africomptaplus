package com.wisedesign.africomptaplus.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.wisedesign.africomptaplus.R;
import com.wisedesign.africomptaplus.models.FinancialReport;
import com.wisedesign.africomptaplus.services.ReportPdfExporter;
import com.wisedesign.africomptaplus.services.ReportService;
import com.wisedesign.africomptaplus.ui.BarChartView;
import com.wisedesign.africomptaplus.ui.BottomNav;
import com.wisedesign.africomptaplus.ui.UiKit;
import java.text.SimpleDateFormat;
import java.util.*;

/** Rapport financier : onglets Jour/Mois/Trim./Sem./Année + graphique + export PDF. */
public class ReportActivity extends AppCompatActivity {

    private ReportService rs;
    private FinancialReport current;
    private String currentPeriod = ReportService.PERIOD_DAY;
    private View[] tabs;
    private final String[] periods = {ReportService.PERIOD_DAY, ReportService.PERIOD_MONTH,
            ReportService.PERIOD_QUARTER, ReportService.PERIOD_SEMESTER, ReportService.PERIOD_YEAR, "custom"};
    private final String[] periodNames = {"Jour", "Mois", "Trimestre", "Semestre", "Année", "Période personnalisée"};
    private String customFrom = "", customTo = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        rs = new ReportService(this);
        UiKit.topBar(this, "Rapport financier");
        UiKit.topAction(this, "Exporter PDF", R.drawable.ic_download, v -> exportPdf());
        BottomNav.attach(this, BottomNav.REPORTS);

        tabs = new View[]{findViewById(R.id.tabDay), findViewById(R.id.tabMonth),
                findViewById(R.id.tabQuarter), findViewById(R.id.tabSemester), findViewById(R.id.tabYear), findViewById(R.id.tabCustom)};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> select(idx));
        }
        select(0);
    }

    private void pickCustom() {
        Calendar c = Calendar.getInstance();
        android.app.DatePickerDialog d1 = new android.app.DatePickerDialog(this, (v, y, m, d) -> {
            final String from = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            android.app.DatePickerDialog d2 = new android.app.DatePickerDialog(this, (v2, y2, m2, dd2) -> {
                String to = String.format(Locale.US, "%04d-%02d-%02d", y2, m2 + 1, dd2);
                customFrom = from.compareTo(to) <= 0 ? from : to;
                customTo   = from.compareTo(to) <= 0 ? to : from;
                currentPeriod = "custom";
                for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(i == tabs.length - 1);
                loadReport();
            }, y, m, d);
            d2.setTitle("Date de fin");
            d2.show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d1.setTitle("Date de début");
        d1.show();
    }

    private void select(int idx) {
        if (idx == periods.length - 1) { pickCustom(); return; }
        currentPeriod = periods[idx];
        for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(i == idx);
        loadReport();
    }

    private void loadReport() {
        String[] b = "custom".equals(currentPeriod) ? new String[]{customFrom, customTo} : rs.periodBounds(currentPeriod);
        SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH);
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            ((TextView) findViewById(R.id.tvPeriod)).setText("Du " + out.format(in.parse(b[0])) + " au " + out.format(in.parse(b[1])));
        } catch (Exception e) { ((TextView) findViewById(R.id.tvPeriod)).setText(b[0] + " → " + b[1]); }

        FinancialReport r = rs.computeReport(b[0], b[1]);
        current = r;
        set(R.id.tvRevenue, UiKit.xaf(r.totalRevenue));
        set(R.id.tvGrossProfit, UiKit.xaf(r.grossProfit));
        set(R.id.tvExpenses, UiKit.xaf(r.totalExpenses));
        TextView net = findViewById(R.id.tvNetProfit);
        net.setText(UiKit.xaf(r.netProfit));
        net.setTextColor(getResources().getColor(r.netProfit >= 0 ? R.color.success : R.color.error));
        set(R.id.tvSalesCount, String.valueOf(r.salesCount));
        set(R.id.tvAvgBasket, UiKit.xaf(r.avgBasket));
        set(R.id.tvDebts, UiKit.xaf(r.totalDebts));

        ((BarChartView) findViewById(R.id.chartRevenue)).setData(r.revenueByDay);

        fillRank(R.id.llTopProducts, r.topProducts, true);
        fillRank(R.id.llTopCategories, r.topCategories, false);
    }

    private void fillRank(int containerId, List<String[]> rows, boolean product) {
        LinearLayout box = findViewById(containerId);
        box.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Aucune vente sur la période");
            t.setTextColor(getResources().getColor(R.color.text_secondary));
            t.setPadding(0, 12, 0, 4);
            box.addView(t);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            View v = LayoutInflater.from(this).inflate(R.layout.item_rank_row, box, false);
            ((TextView) v.findViewById(R.id.tvRank)).setText(String.valueOf(i + 1));
            ((TextView) v.findViewById(R.id.tvRankName)).setText(r[0]);
            TextView sub = v.findViewById(R.id.tvRankSub);
            if (product) sub.setText(r[1] + " vente(s)"); else sub.setVisibility(View.GONE);
            double amt = Double.parseDouble(product ? r[2] : r[1]);
            ((TextView) v.findViewById(R.id.tvRankValue)).setText(UiKit.xaf(amt));
            box.addView(v);
        }
    }

    private void exportPdf() {
        if (current == null) return;
        Uri uri = new ReportPdfExporter(this).export(current, periodNames[Arrays.asList(periods).indexOf(currentPeriod)]);
        if (uri == null) { Toast.makeText(this, "Erreur de génération du PDF", Toast.LENGTH_SHORT).show(); return; }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, "Rapport financier");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Partager le rapport"));
    }

    private void set(int id, String t) { ((TextView) findViewById(id)).setText(t); }
}
