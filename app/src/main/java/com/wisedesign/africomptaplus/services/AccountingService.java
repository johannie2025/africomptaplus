package com.wisedesign.africomptaplus.services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Comptabilité SYSCOHADA dérivée des opérations de l'app (ventes, règlements de dettes, dépenses)
 * + écritures manuelles. Les écritures automatiques sont recalculées à la volée : activer la
 * comptabilité plus tard couvre tout l'historique.
 *
 * Écriture de vente : D 571/5712/411 (TTC) ; C 7011 par catégorie (HT, axe analytique) ; C 4431 (TVA) ;
 * D 6011 par catégorie / C 311 (coût d'achat — inventaire permanent). Les achats de stock
 * (dépense « Stock » ou saisie manuelle) débitent 311.
 */
public class AccountingService {
    public static final String CASH = "571", MOBILE = "5712", CLIENTS = "411", SALES = "7011",
            VAT = "4431", COGS = "6011", STOCK = "311", MISC_INCOME = "758", MISC_EXPENSE = "658";

    public static class Line { public String account; public double debit, credit; public String analytic = ""; }
    public static class Entry {
        public String date, journal, ref = "", label, kind = "manual";
        public long manualId = -1;
        public final List<Line> lines = new ArrayList<>();
        Entry line(String acc, double d, double c, String an) {
            Line l = new Line(); l.account = acc; l.debit = d; l.credit = c; l.analytic = an == null ? "" : an;
            lines.add(l); return this;
        }
    }

    private final DatabaseHelper db;
    public AccountingService(Context ctx) { db = DatabaseHelper.getInstance(ctx); }

    public String mode() {
        String m = db.getConfig("acct_mode");
        return (m == null || m.isEmpty()) ? "off" : m;
    }

    // ── Comptes ─────────────────────────────────────────────────────────────
    public Map<String, String> accounts() {
        TreeMap<String, String> m = new TreeMap<>(OhadaPlan.all());
        Cursor c = db.rawQuery("SELECT code,label FROM " + DatabaseHelper.T_ACCT_ACCOUNTS, null);
        try { while (c.moveToNext()) m.put(c.getString(0), c.getString(1)); } finally { c.close(); }
        return m;
    }
    public String label(String code) {
        String l = accounts().get(code);
        return l != null ? l : "Compte " + code;
    }
    public void addAccount(String code, String label) {
        ContentValues cv = new ContentValues();
        cv.put("code", code); cv.put("label", label);
        db.getWritableDatabase().insertWithOnConflict(DatabaseHelper.T_ACCT_ACCOUNTS, null, cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String expenseAccount(String category) {
        String c = category == null ? "" : java.text.Normalizer.normalize(category, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (c.contains("stock") || c.contains("marchandise")) return STOCK;
        if (c.contains("loyer") || c.contains("location")) return "6222";
        if (c.contains("personnel") || c.contains("salaire") || c.contains("paie")) return "661";
        if (c.contains("transport") || c.contains("deplacement")) return "618";
        if (c.contains("electric") || c.contains("courant")) return "6052";
        if (c.contains("eau")) return "6051";
        if (c.contains("carburant") || c.contains("essence")) return "6053";
        if (c.contains("telephone") || c.contains("internet") || c.contains("communication") || c.contains("credit")) return "628";
        if (c.contains("publicite") || c.contains("marketing")) return "627";
        if (c.contains("fourniture") || c.contains("bureau")) return "6055";
        if (c.contains("entretien") || c.contains("reparation") || c.contains("maintenance")) return "624";
        if (c.contains("assurance")) return "625";
        if (c.contains("impot") || c.contains("taxe")) return "641";
        if (c.contains("banque") || c.contains("bancaire")) return "631";
        return MISC_EXPENSE;
    }

    // ── Moteur d'écritures ──────────────────────────────────────────────────
    private static String d10(String s) { return s != null && s.length() >= 10 ? s.substring(0, 10) : (s == null ? "" : s); }

    private String range(String col, String from, String to, List<String> args) {
        StringBuilder sb = new StringBuilder();
        if (from != null && !from.isEmpty()) { sb.append(" AND date(").append(col).append(")>=date(?)"); args.add(from); }
        if (to != null && !to.isEmpty())     { sb.append(" AND date(").append(col).append(")<=date(?)"); args.add(to); }
        return sb.toString();
    }

    /** Toutes les écritures (auto + manuelles) de la période, triées par date. */
    public List<Entry> entries(String from, String to) {
        List<Entry> out = new ArrayList<>();

        // ventes : ventilation par catégorie
        List<String> a1 = new ArrayList<>();
        Map<Long, List<Object[]>> cats = new HashMap<>();
        Cursor ic = db.rawQuery("SELECT si.sale_id, COALESCE(p.category,'Général'), SUM(si.total_price), SUM(si.quantity*p.buying_price)"
                + " FROM sale_items si JOIN sales s ON s.id=si.sale_id JOIN products p ON p.id=si.product_id WHERE 1=1"
                + range("s.created_at", from, to, a1) + " GROUP BY si.sale_id, p.category", a1.toArray(new String[0]));
        try {
            while (ic.moveToNext()) {
                List<Object[]> l = cats.get(ic.getLong(0));
                if (l == null) { l = new ArrayList<>(); cats.put(ic.getLong(0), l); }
                l.add(new Object[]{ic.getString(1), ic.getDouble(2), ic.getDouble(3)});
            }
        } finally { ic.close(); }

        List<String> a2 = new ArrayList<>();
        Cursor sc = db.rawQuery("SELECT id, invoice_number, client_name, total_amount, tax_amount, payment_method, created_at FROM sales WHERE 1=1"
                + range("created_at", from, to, a2) + " ORDER BY created_at, id", a2.toArray(new String[0]));
        try {
            while (sc.moveToNext()) {
                long id = sc.getLong(0);
                double total = sc.getDouble(3), tax = sc.getDouble(4);
                String pm = sc.getString(5);
                Entry e = new Entry();
                e.date = d10(sc.getString(6)); e.journal = "VT"; e.ref = sc.getString(1); e.kind = "sale";
                e.label = "Vente " + sc.getString(1) + " – " + sc.getString(2);
                String tender = "credit".equals(pm) ? CLIENTS : "mobile_money".equals(pm) ? MOBILE : CASH;
                e.line(tender, total, 0, "");
                List<Object[]> cl = cats.get(id);
                double sumRev = 0;
                if (cl != null) for (Object[] r : cl) sumRev += (Double) r[1];
                double diff = total - tax - sumRev;
                boolean first = true;
                if (cl != null) {
                    for (Object[] r : cl) {
                        double rev = (Double) r[1] + (first && Math.abs(diff) >= 0.5 ? diff : 0);
                        first = false;
                        e.line(SALES, 0, rev, (String) r[0]);
                    }
                } else e.line(SALES, 0, total - tax, "");
                if (tax > 0) e.line(VAT, 0, tax, "");
                double cost = 0;
                if (cl != null) for (Object[] r : cl) {
                    double c = (Double) r[2];
                    if (c > 0) { e.line(COGS, c, 0, (String) r[0]); cost += c; }
                }
                if (cost > 0) e.line(STOCK, 0, cost, "");
                out.add(e);
            }
        } finally { sc.close(); }

        // règlements de dettes
        List<String> a3 = new ArrayList<>();
        Cursor pc = db.rawQuery("SELECT p.id, p.amount, p.method, p.created_at, c.name FROM debt_payments p"
                + " JOIN debts d ON d.id=p.debt_id LEFT JOIN clients c ON c.id=d.client_id WHERE 1=1"
                + range("p.created_at", from, to, a3) + " ORDER BY p.created_at, p.id", a3.toArray(new String[0]));
        try {
            while (pc.moveToNext()) {
                Entry e = new Entry();
                boolean mm = "mobile_money".equals(pc.getString(2));
                e.date = d10(pc.getString(3)); e.journal = mm ? "BQ" : "CA"; e.kind = "debt";
                e.ref = "REC-" + String.format(Locale.US, "%05d", pc.getLong(0));
                e.label = "Règlement dette " + pc.getString(4) + " (" + e.ref + ")";
                e.line(mm ? MOBILE : CASH, pc.getDouble(1), 0, "").line(CLIENTS, 0, pc.getDouble(1), "");
                out.add(e);
            }
        } finally { pc.close(); }

        // dépenses
        List<String> a4 = new ArrayList<>();
        Cursor ec = db.rawQuery("SELECT id, category, description, amount, date FROM expenses WHERE 1=1"
                + range("date", from, to, a4) + " ORDER BY date, id", a4.toArray(new String[0]));
        try {
            while (ec.moveToNext()) {
                String cat = ec.getString(1);
                String acc = expenseAccount(cat);
                String desc = ec.getString(2);
                Entry e = new Entry();
                e.date = d10(ec.getString(4)); e.journal = "AC"; e.ref = "DEP-" + ec.getLong(0);
                e.kind = STOCK.equals(acc) ? "stock" : "expense";
                e.label = (STOCK.equals(acc) ? "Achat de stock" : "Dépense " + cat) + (desc != null && !desc.isEmpty() ? " – " + desc : "");
                e.line(acc, ec.getDouble(3), 0, STOCK.equals(acc) ? "" : cat).line(CASH, 0, ec.getDouble(3), "");
                out.add(e);
            }
        } finally { ec.close(); }

        // écritures manuelles
        List<String> a5 = new ArrayList<>();
        Cursor mc = db.rawQuery("SELECT id, entry_date, journal, label, debit_account, credit_account, amount FROM acct_entries WHERE 1=1"
                + range("entry_date", from, to, a5) + " ORDER BY entry_date, id", a5.toArray(new String[0]));
        try {
            while (mc.moveToNext()) {
                Entry e = new Entry();
                e.manualId = mc.getLong(0); e.date = d10(mc.getString(1)); e.journal = mc.getString(2);
                e.label = mc.getString(3); e.ref = "SAI-" + mc.getLong(0); e.kind = "manual";
                e.line(mc.getString(4), mc.getDouble(6), 0, "").line(mc.getString(5), 0, mc.getDouble(6), "");
                out.add(e);
            }
        } finally { mc.close(); }

        Collections.sort(out, (x, y) -> x.date.compareTo(y.date));
        return out;
    }

    private static String n(double v) { return v == 0 ? "" : UiKit.fmt(v); }
    private static boolean match(String q, String... cells) {
        if (q == null || q.trim().isEmpty()) return true;
        String l = q.trim().toLowerCase(Locale.ROOT);
        for (String c : cells) if (c != null && c.toLowerCase(Locale.ROOT).contains(l)) return true;
        return false;
    }
    private static String dshow(String iso) {
        return iso != null && iso.length() >= 10 ? iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4) : "";
    }
    private static String sold(double s) { return s == 0 ? "0" : UiKit.fmt(Math.abs(s)) + (s > 0 ? " D" : " C"); }

    // ── États ───────────────────────────────────────────────────────────────
    public ReportTable journal(String from, String to, String q) {
        ReportTable t = new ReportTable("Journal général", new String[]{"Date", "Jnl", "Compte", "Libellé", "Débit", "Crédit"},
                new float[]{1.15f, 0.6f, 0.85f, 2.3f, 1.15f, 1.15f}, new boolean[]{false, false, false, false, true, true});
        double td = 0, tc = 0;
        for (Entry e : entries(from, to)) {
            boolean ok = match(q, e.label, e.ref, e.journal);
            if (!ok) for (Line l : e.lines) if (match(q, l.account, label(l.account))) { ok = true; break; }
            if (!ok) continue;
            boolean first = true;
            for (Line l : e.lines) {
                t.add(false, first ? dshow(e.date) : "", first ? e.journal : "", l.account,
                        first ? e.label : "   " + label(l.account), n(l.debit), n(l.credit));
                first = false; td += l.debit; tc += l.credit;
            }
        }
        t.add(true, "", "", "", "TOTAUX", UiKit.fmt(td), UiKit.fmt(tc));
        return t;
    }

    public ReportTable ledger(String from, String to, String q) {
        ReportTable t = new ReportTable("Grand livre", new String[]{"Date", "Jnl", "Libellé", "Débit", "Crédit", "Solde"},
                new float[]{1.15f, 0.6f, 2.2f, 1.1f, 1.1f, 1.25f}, new boolean[]{false, false, false, true, true, true});
        TreeMap<String, List<Object[]>> by = new TreeMap<>();
        for (Entry e : entries(from, to))
            for (Line l : e.lines) {
                List<Object[]> lst = by.get(l.account);
                if (lst == null) { lst = new ArrayList<>(); by.put(l.account, lst); }
                lst.add(new Object[]{e, l});
            }
        for (Map.Entry<String, List<Object[]>> g : by.entrySet()) {
            if (!match(q, g.getKey(), label(g.getKey()))) continue;
            t.add(true, "", "", g.getKey() + " – " + label(g.getKey()));
            double run = 0, td = 0, tc = 0;
            for (Object[] o : g.getValue()) {
                Entry e = (Entry) o[0]; Line l = (Line) o[1];
                run += l.debit - l.credit; td += l.debit; tc += l.credit;
                t.add(false, dshow(e.date), e.journal, e.label, n(l.debit), n(l.credit), sold(run));
            }
            t.add(true, "", "", "Total " + g.getKey(), UiKit.fmt(td), UiKit.fmt(tc), sold(td - tc));
        }
        return t;
    }

    private TreeMap<String, double[]> totals(String from, String to) {
        TreeMap<String, double[]> m = new TreeMap<>();
        for (Entry e : entries(from, to))
            for (Line l : e.lines) {
                double[] v = m.get(l.account);
                if (v == null) { v = new double[2]; m.put(l.account, v); }
                v[0] += l.debit; v[1] += l.credit;
            }
        return m;
    }

    public ReportTable balance(String from, String to, String q) {
        ReportTable t = new ReportTable("Balance générale", new String[]{"Compte", "Intitulé", "Débit", "Crédit", "Solde D", "Solde C"},
                new float[]{0.8f, 2.1f, 1.05f, 1.05f, 1.05f, 1.05f}, new boolean[]{false, false, true, true, true, true});
        double td = 0, tc = 0, sd = 0, sc = 0;
        for (Map.Entry<String, double[]> e : totals(from, to).entrySet()) {
            if (!match(q, e.getKey(), label(e.getKey()))) continue;
            double d = e.getValue()[0], c = e.getValue()[1], s = d - c;
            t.add(false, e.getKey(), label(e.getKey()), n(d), n(c), s > 0 ? UiKit.fmt(s) : "", s < 0 ? UiKit.fmt(-s) : "");
            td += d; tc += c; if (s > 0) sd += s; else sc -= s;
        }
        t.add(true, "", "TOTAUX", UiKit.fmt(td), UiKit.fmt(tc), UiKit.fmt(sd), UiKit.fmt(sc));
        return t;
    }

    public ReportTable income(String from, String to) {
        ReportTable t = new ReportTable("Compte de résultat", new String[]{"Compte", "Libellé", "Montant"},
                new float[]{0.9f, 3f, 1.6f}, new boolean[]{false, false, true});
        TreeMap<String, double[]> m = totals(from, to);
        double prod = 0, chg = 0, ventes = 0, achats = 0;
        t.add(true, "", "PRODUITS (classe 7)", "");
        for (Map.Entry<String, double[]> e : m.entrySet()) if (e.getKey().startsWith("7")) {
            double v = e.getValue()[1] - e.getValue()[0]; prod += v;
            if (e.getKey().startsWith("70")) ventes += v;
            t.add(false, e.getKey(), label(e.getKey()), UiKit.fmt(v));
        }
        t.add(true, "", "Total produits", UiKit.fmt(prod));
        t.add(true, "", "CHARGES (classe 6)", "");
        for (Map.Entry<String, double[]> e : m.entrySet()) if (e.getKey().startsWith("6")) {
            double v = e.getValue()[0] - e.getValue()[1]; chg += v;
            if (e.getKey().startsWith("60")) achats += v;
            t.add(false, e.getKey(), label(e.getKey()), UiKit.fmt(v));
        }
        t.add(true, "", "Total charges", UiKit.fmt(chg));
        t.add(true, "", "Marge commerciale (ventes − coût d'achat)", UiKit.fmt(ventes - achats));
        t.add(true, "", prod - chg >= 0 ? "RÉSULTAT NET : BÉNÉFICE" : "RÉSULTAT NET : PERTE", UiKit.fmt(prod - chg));
        return t;
    }

    /** Bilan cumulé jusqu'à `to` (les soldes débiteurs vont à l'actif, créditeurs au passif). */
    public ReportTable balanceSheet(String to) {
        ReportTable t = new ReportTable("Bilan", new String[]{"Compte", "Libellé", "Montant"},
                new float[]{0.9f, 3f, 1.6f}, new boolean[]{false, false, true});
        TreeMap<String, double[]> m = totals("", to);
        double actif = 0, passif = 0, res = 0;
        List<String[]> a = new ArrayList<>(), p = new ArrayList<>();
        for (Map.Entry<String, double[]> e : m.entrySet()) {
            char cl = e.getKey().charAt(0);
            double s = e.getValue()[0] - e.getValue()[1];
            if (cl == '7') res += -s;
            else if (cl == '6') res -= s;
            else if (cl >= '1' && cl <= '5') {
                if (s > 0) { a.add(new String[]{e.getKey(), label(e.getKey()), UiKit.fmt(s)}); actif += s; }
                else if (s < 0) { p.add(new String[]{e.getKey(), label(e.getKey()), UiKit.fmt(-s)}); passif += -s; }
            }
        }
        t.add(true, "", "ACTIF", "");
        for (String[] r : a) t.add(false, r);
        if (res < 0) { t.add(false, "139", "Résultat net : perte", UiKit.fmt(-res)); actif += -res; }
        t.add(true, "", "TOTAL ACTIF", UiKit.fmt(actif));
        t.add(true, "", "PASSIF", "");
        for (String[] r : p) t.add(false, r);
        if (res > 0) { t.add(false, "131", "Résultat net : bénéfice", UiKit.fmt(res)); passif += res; }
        t.add(true, "", "TOTAL PASSIF", UiKit.fmt(passif));
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(stock*buying_price),0) FROM products", null);
        try { if (c.moveToFirst()) t.add(false, "", "Pour info : stock valorisé au coût d'achat (inventaire)", UiKit.fmt(c.getDouble(0))); }
        finally { c.close(); }
        return t;
    }

    /** Comptabilité analytique : centres = catégories de produits (ventilation automatique) + charges par nature. */
    public ReportTable analytic(String from, String to) {
        ReportTable t = new ReportTable("Comptabilité analytique", new String[]{"Centre / nature", "CA HT", "Coûts", "Marge", "Taux"},
                new float[]{2.2f, 1.2f, 1.2f, 1.2f, 0.8f}, new boolean[]{false, true, true, true, true});
        TreeMap<String, double[]> centers = new TreeMap<>();
        TreeMap<String, Double> charges = new TreeMap<>();
        for (Entry e : entries(from, to))
            for (Line l : e.lines) {
                if (SALES.equals(l.account) || COGS.equals(l.account)) {
                    String k = l.analytic.isEmpty() ? "Non ventilé" : l.analytic;
                    double[] v = centers.get(k); if (v == null) { v = new double[2]; centers.put(k, v); }
                    if (SALES.equals(l.account)) v[0] += l.credit - l.debit; else v[1] += l.debit - l.credit;
                } else if (l.account.startsWith("6") && !l.account.startsWith("60")) {
                    String k = label(l.account);
                    Double cur = charges.get(k);
                    charges.put(k, (cur == null ? 0 : cur) + l.debit - l.credit);
                }
            }
        t.add(true, "CENTRES : CATÉGORIES DE PRODUITS");
        double ca = 0, co = 0;
        for (Map.Entry<String, double[]> e : centers.entrySet()) {
            double v = e.getValue()[0], c = e.getValue()[1], m = v - c;
            t.add(false, e.getKey(), UiKit.fmt(v), UiKit.fmt(c), UiKit.fmt(m), v > 0 ? Math.round(m * 100 / v) + " %" : "");
            ca += v; co += c;
        }
        t.add(true, "Total marge brute", UiKit.fmt(ca), UiKit.fmt(co), UiKit.fmt(ca - co), ca > 0 ? Math.round((ca - co) * 100 / ca) + " %" : "");
        t.add(true, "CHARGES D'EXPLOITATION PAR NATURE");
        double tc = 0;
        for (Map.Entry<String, Double> e : charges.entrySet()) { t.add(false, e.getKey(), "", UiKit.fmt(e.getValue())); tc += e.getValue(); }
        t.add(true, "Total charges", "", UiKit.fmt(tc));
        t.add(true, ca - co - tc >= 0 ? "RÉSULTAT ANALYTIQUE (bénéfice)" : "RÉSULTAT ANALYTIQUE (perte)", "", "", UiKit.fmt(ca - co - tc));
        return t;
    }

    // ── Comptabilité simple (trésorerie) ────────────────────────────────────
    public ReportTable cashBook(String from, String to, String q) {
        ReportTable t = new ReportTable("Livre de caisse", new String[]{"Date", "Libellé", "Entrée", "Sortie", "Solde"},
                new float[]{1.15f, 2.4f, 1.15f, 1.15f, 1.3f}, new boolean[]{false, false, true, true, true});
        double run = 0;
        if (from != null && !from.isEmpty()) {
            for (Entry e : entries("", dayBefore(from))) run += cashNet(e);
            t.add(true, "", "Report à nouveau", "", "", UiKit.fmt(run));
        }
        double in = 0, outv = 0;
        for (Entry e : entries(from, to)) {
            double net = cashNet(e);
            if (net == 0 || !match(q, e.label, e.ref)) { if (net != 0) run += net; continue; }
            run += net;
            t.add(false, dshow(e.date), e.label, net > 0 ? UiKit.fmt(net) : "", net < 0 ? UiKit.fmt(-net) : "", UiKit.fmt(run));
            if (net > 0) in += net; else outv -= net;
        }
        t.add(true, "", "TOTAUX", UiKit.fmt(in), UiKit.fmt(outv), UiKit.fmt(run));
        return t;
    }

    private static double cashNet(Entry e) {
        double v = 0;
        for (Line l : e.lines) if (CASH.equals(l.account) || MOBILE.equals(l.account)) v += l.debit - l.credit;
        return v;
    }

    private static String dayBefore(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar c = Calendar.getInstance(); c.setTime(f.parse(iso)); c.add(Calendar.DAY_OF_YEAR, -1);
            return f.format(c.getTime());
        } catch (Exception e) { return iso; }
    }

    public ReportTable simpleSummary(String from, String to) {
        ReportTable t = new ReportTable("Synthèse de trésorerie", new String[]{"Libellé", "Montant"},
                new float[]{3f, 1.6f}, new boolean[]{false, true});
        double sale = 0, debt = 0, otherIn = 0, exp = 0, stock = 0, otherOut = 0;
        for (Entry e : entries(from, to)) {
            double net = cashNet(e);
            switch (e.kind) {
                case "sale": sale += net; break;
                case "debt": debt += net; break;
                case "expense": exp += -net; break;
                case "stock": stock += -net; break;
                default: if (net >= 0) otherIn += net; else otherOut += -net;
            }
        }
        double in = sale + debt + otherIn, out = exp + stock + otherOut;
        t.add(true, "ENTRÉES", "");
        t.add(false, "Ventes encaissées (espèces + mobile)", UiKit.fmt(sale));
        t.add(false, "Règlements de dettes clients", UiKit.fmt(debt));
        t.add(false, "Autres entrées", UiKit.fmt(otherIn));
        t.add(true, "Total entrées", UiKit.fmt(in));
        t.add(true, "SORTIES", "");
        t.add(false, "Achats de stock", UiKit.fmt(stock));
        t.add(false, "Charges et dépenses", UiKit.fmt(exp));
        t.add(false, "Autres sorties", UiKit.fmt(otherOut));
        t.add(true, "Total sorties", UiKit.fmt(out));
        t.add(true, "SOLDE DE LA PÉRIODE", UiKit.fmt(in - out));
        double cli = 0;
        for (Entry e : entries("", to)) for (Line l : e.lines) if (CLIENTS.equals(l.account)) cli += l.debit - l.credit;
        t.add(false, "Créances clients restant à encaisser (cumul)", UiKit.fmt(cli));
        return t;
    }

    public ReportTable plan(String q) {
        ReportTable t = new ReportTable("Plan comptable SYSCOHADA", new String[]{"Compte", "Intitulé"},
                new float[]{1f, 4f}, new boolean[]{false, false});
        for (Map.Entry<String, String> e : accounts().entrySet())
            if (match(q, e.getKey(), e.getValue())) t.add(e.getKey().length() <= 2, e.getKey(), e.getValue());
        return t;
    }

    // ── Saisies manuelles ───────────────────────────────────────────────────
    public long addManual(String date, String journal, String label, String debit, String credit, double amount) {
        ContentValues cv = new ContentValues();
        cv.put("entry_date", date); cv.put("journal", journal); cv.put("label", label);
        cv.put("debit_account", debit); cv.put("credit_account", credit); cv.put("amount", amount);
        cv.put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        return db.insert(DatabaseHelper.T_ACCT_ENTRIES, cv);
    }
    public void deleteManual(long id) { db.delete(DatabaseHelper.T_ACCT_ENTRIES, "id=?", new String[]{String.valueOf(id)}); }

    public ReportTable manualList(String from, String to, String q) {
        ReportTable t = new ReportTable("Écritures saisies (appuyez pour supprimer)", new String[]{"Date", "Jnl", "Libellé", "Débit → Crédit", "Montant"},
                new float[]{1.15f, 0.6f, 2f, 1.4f, 1.1f}, new boolean[]{false, false, false, false, true});
        for (Entry e : entries(from, to)) {
            if (e.manualId < 0 || !match(q, e.label, e.ref)) continue;
            t.add(false, e.manualId, dshow(e.date), e.journal, e.label,
                    e.lines.get(0).account + " → " + e.lines.get(1).account, UiKit.fmt(e.lines.get(0).debit));
        }
        return t;
    }
}
