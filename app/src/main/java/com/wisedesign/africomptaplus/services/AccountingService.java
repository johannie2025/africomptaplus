package com.wisedesign.africomptaplus.services;

import com.wisedesign.africomptaplus.ui.I18n;

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
            VAT = "4431", COGS = "6011", STOCK = "311", SUPPLIERS = "401", MISC_INCOME = "758", MISC_EXPENSE = "658", STOCK_VAR = "6031";

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
    public AccountingService(Context ctx) { db = DatabaseHelper.getInstance(ctx); try { migrateOpenings(); } catch (Exception e) { android.util.Log.w("AccountingService", "migration stock: " + e); } }

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
        Cursor ic = db.rawQuery("SELECT si.sale_id, COALESCE(p.category,'Général'), SUM(si.total_price), SUM(si.quantity*CASE WHEN si.unit_cost>0 THEN si.unit_cost ELSE p.buying_price END)"
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

        // achats fournisseurs : D 311 ; C caisse/mobile (acompte) ; C 401 (reste)
        List<String> a6 = new ArrayList<>();
        Cursor uc = db.rawQuery("SELECT id, reference, supplier_name, total_amount, initial_paid, payment_method, created_at FROM purchases WHERE 1=1"
                + range("created_at", from, to, a6) + " ORDER BY created_at, id", a6.toArray(new String[0]));
        try {
            while (uc.moveToNext()) {
                double total = uc.getDouble(3), paid = uc.getDouble(4);
                Entry e = new Entry();
                e.date = d10(uc.getString(6)); e.journal = "AC"; e.ref = uc.getString(1); e.kind = "stock";
                e.label = "Achat " + uc.getString(1) + " – " + uc.getString(2);
                e.line(STOCK, total, 0, "");
                if (paid > 0) e.line("mobile_money".equals(uc.getString(5)) ? MOBILE : CASH, 0, paid, "");
                if (total - paid > 0.001) e.line(SUPPLIERS, 0, total - paid, "");
                out.add(e);
            }
        } finally { uc.close(); }

        // règlements fournisseurs : D 401 / C caisse ou mobile
        List<String> a7 = new ArrayList<>();
        Cursor fc = db.rawQuery("SELECT sp.id, sp.amount, sp.method, sp.created_at, p.supplier_name, p.reference FROM supplier_payments sp"
                + " JOIN purchases p ON p.id=sp.purchase_id WHERE 1=1" + range("sp.created_at", from, to, a7)
                + " ORDER BY sp.created_at, sp.id", a7.toArray(new String[0]));
        try {
            while (fc.moveToNext()) {
                boolean mm = "mobile_money".equals(fc.getString(2));
                Entry e = new Entry();
                e.date = d10(fc.getString(3)); e.journal = mm ? "BQ" : "CA"; e.kind = "stock";
                e.ref = "RGF-" + String.format(Locale.US, "%05d", fc.getLong(0));
                e.label = "Règlement fournisseur " + fc.getString(4) + " (" + fc.getString(5) + ")";
                e.line(SUPPLIERS, fc.getDouble(1), 0, "").line(mm ? MOBILE : CASH, 0, fc.getDouble(1), "");
                out.add(e);
            }
        } finally { fc.close(); }

        // inventaires physiques : écart négatif = D 6031 / C 311 ; positif = D 311 / C 6031 (valorisés au CMP)
        List<String> aInv = new ArrayList<>();
        Cursor vc = db.rawQuery("SELECT v.reference, v.inv_date, v.note,"
                + " COALESCE(SUM(CASE WHEN i.counted<i.theoretical THEN (i.theoretical-i.counted)*i.cmp ELSE 0 END),0),"
                + " COALESCE(SUM(CASE WHEN i.counted>i.theoretical THEN (i.counted-i.theoretical)*i.cmp ELSE 0 END),0)"
                + " FROM inventories v LEFT JOIN inventory_items i ON i.inventory_id=v.id WHERE 1=1" + range("v.inv_date", from, to, aInv) + " GROUP BY v.id ORDER BY v.inv_date, v.id", aInv.toArray(new String[0]));
        try {
            while (vc.moveToNext()) {
                double loss = Math.round(vc.getDouble(3)), gain = Math.round(vc.getDouble(4));
                if (loss <= 0 && gain <= 0) continue;
                Entry e = new Entry(); e.date = d10(vc.getString(1)); e.journal = "OD"; e.ref = vc.getString(0); e.kind = "stock";
                e.label = I18n.t("Inventaire physique") + " " + vc.getString(0) + (vc.getString(2) != null && !vc.getString(2).isEmpty() ? " – " + vc.getString(2) : "");
                if (loss > 0) e.line(STOCK_VAR, loss, 0, "").line(STOCK, 0, loss, "");
                if (gain > 0) e.line(STOCK, gain, 0, "").line(STOCK_VAR, 0, gain, "");
                out.add(e);
            }
        } finally { vc.close(); }

        // mouvements de stock manuels : stock initial D 311 / C 101 ; entrée D 311 / C 6031 ; sortie D 6031 / C 311
        List<String> aMv = new ArrayList<>();
        Cursor mc = db.rawQuery("SELECT id, kind, product_name, qty, unit_cost, move_date, reason FROM stock_moves WHERE 1=1" + range("move_date", from, to, aMv) + " ORDER BY move_date, id", aMv.toArray(new String[0]));
        try {
            while (mc.moveToNext()) {
                double v = Math.round(mc.getInt(3) * mc.getDouble(4)); if (v <= 0) continue;
                String k = mc.getString(1), rs = mc.getString(6) == null ? "" : mc.getString(6);
                Entry e = new Entry(); e.date = d10(mc.getString(5)); e.journal = "OD"; e.ref = "MVT-" + mc.getLong(0); e.kind = "stock";
                e.label = ("opening".equals(k) ? I18n.t("Stock initial") : !rs.isEmpty() ? rs : "out".equals(k) ? I18n.t("Sortie de stock") : I18n.t("Entrée de stock")) + " – " + mc.getString(2) + " ×" + mc.getInt(3);
                if ("out".equals(k)) e.line(STOCK_VAR, v, 0, "").line(STOCK, 0, v, "");
                else e.line(STOCK, v, 0, "").line("opening".equals(k) ? "101" : STOCK_VAR, 0, v, "");
                out.add(e);
            }
        } finally { mc.close(); }

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
        ReportTable t = new ReportTable(I18n.t("Journal général"), new String[]{I18n.t("Date"), I18n.t("Jnl"), I18n.t("Compte"), I18n.t("Libellé"), I18n.t("Débit"), I18n.t("Crédit")},
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
        ReportTable t = new ReportTable(I18n.t("Grand livre"), new String[]{I18n.t("Date"), I18n.t("Jnl"), I18n.t("Libellé"), I18n.t("Débit"), I18n.t("Crédit"), I18n.t("Solde")},
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
        ReportTable t = new ReportTable(I18n.t("Balance générale"), new String[]{I18n.t("Compte"), I18n.t("Intitulé"), I18n.t("Débit"), I18n.t("Crédit"), I18n.t("Solde D"), I18n.t("Solde C")},
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
        ReportTable t = new ReportTable(I18n.t("Compte de résultat"), new String[]{I18n.t("Compte"), I18n.t("Libellé"), I18n.t("Montant")},
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
        ReportTable t = new ReportTable(I18n.t("Bilan"), new String[]{I18n.t("Compte"), I18n.t("Libellé"), I18n.t("Montant")},
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
        ReportTable t = new ReportTable(I18n.t("Comptabilité analytique"), new String[]{I18n.t("Centre / nature"), "CA HT", I18n.t("Coûts"), I18n.t("Marge"), I18n.t("Taux")},
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
        ReportTable t = new ReportTable(I18n.t("Livre de caisse"), new String[]{I18n.t("Date"), I18n.t("Libellé"), I18n.t("Entrée"), I18n.t("Sortie"), I18n.t("Solde")},
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
        ReportTable t = new ReportTable(I18n.t("Synthèse de trésorerie"), new String[]{I18n.t("Libellé"), I18n.t("Montant")},
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
        double fou = 0;
        for (Entry e : entries("", to)) for (Line l : e.lines) if (SUPPLIERS.equals(l.account)) fou += l.credit - l.debit;
        t.add(false, "Dettes fournisseurs restant à payer (cumul)", UiKit.fmt(fou));
        return t;
    }

    public ReportTable plan(String q) {
        ReportTable t = new ReportTable(I18n.t("Plan comptable SYSCOHADA"), new String[]{I18n.t("Compte"), I18n.t("Intitulé")},
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
        ReportTable t = new ReportTable(I18n.t("Écritures saisies (appuyez pour supprimer)"), new String[]{I18n.t("Date"), I18n.t("Jnl"), I18n.t("Libellé"), I18n.t("Débit → Crédit"), I18n.t("Montant")},
                new float[]{1.15f, 0.6f, 2f, 1.4f, 1.1f}, new boolean[]{false, false, false, false, true});
        for (Entry e : entries(from, to)) {
            if (e.manualId < 0 || !match(q, e.label, e.ref)) continue;
            t.add(false, e.manualId, dshow(e.date), e.journal, e.label,
                    e.lines.get(0).account + " → " + e.lines.get(1).account, UiKit.fmt(e.lines.get(0).debit));
        }
        return t;
    }

    // ── Inventaire valorisé au CMP ──────────────────────────────────────────
    // ── Registre de stock & CMP exact ───────────────────────────────────────
    /** Événements [date, type(0 entrée au coût, 1 sortie, 2 entrée au CMP), qté, coût, prio, libellé, réf] par produit. */
    private Map<Long, List<Object[]>> events(boolean withOpening) {
        Map<Long, List<Object[]>> ev = new HashMap<>();
        Cursor c = db.rawQuery("SELECT pi.product_id, pi.quantity, pi.unit_cost, p.created_at, p.reference, p.supplier_name FROM purchase_items pi JOIN purchases p ON p.id=pi.purchase_id", null);
        try { while (c.moveToNext()) add(ev, c.getLong(0), new Object[]{n(c.getString(3)), 0, (double) c.getInt(1), c.getDouble(2), 1, "Approvisionnement " + n(c.getString(5)), n(c.getString(4))}); } finally { c.close(); }
        c = db.rawQuery("SELECT si.product_id, si.quantity, s.created_at, s.invoice_number FROM sale_items si JOIN sales s ON s.id=si.sale_id", null);
        try { while (c.moveToNext()) add(ev, c.getLong(0), new Object[]{n(c.getString(2)), 1, (double) c.getInt(1), 0.0, 3, "Vente", n(c.getString(3))}); } finally { c.close(); }
        c = db.rawQuery("SELECT product_id, kind, qty, unit_cost, move_date, reason, note FROM stock_moves", null);
        try {
            while (c.moveToNext()) {
                String k = c.getString(1); if ("opening".equals(k) && !withOpening) continue;
                String rs = n(c.getString(5));
                add(ev, c.getLong(0), new Object[]{n(c.getString(4)), "out".equals(k) ? 1 : 0, (double) c.getInt(2), c.getDouble(3), "opening".equals(k) ? 0 : "out".equals(k) ? 3 : 1,
                        "opening".equals(k) ? "Stock initial" : !rs.isEmpty() ? rs : "out".equals(k) ? "Sortie" : "Entrée", n(c.getString(6))});
            }
        } finally { c.close(); }
        c = db.rawQuery("SELECT i.product_id, i.counted-i.theoretical, v.created_at, v.reference FROM inventory_items i JOIN inventories v ON v.id=i.inventory_id WHERE i.counted<>i.theoretical", null);
        try { while (c.moveToNext()) { int dq = c.getInt(1); add(ev, c.getLong(0), new Object[]{n(c.getString(2)), dq > 0 ? 2 : 1, (double) Math.abs(dq), 0.0, dq > 0 ? 2 : 3, "Inventaire physique", n(c.getString(3))}); } } finally { c.close(); }
        return ev;
    }
    private static String n(String s) { return s == null ? "" : s; }

    /** CMP exact par rejeu chronologique du registre (stock initial, achats, ventes, mouvements, inventaires). */
    public Map<Long, Double> cmpMap() {
        Map<Long, List<Object[]>> ev = events(true);
        Map<Long, Double> out = new HashMap<>();
        Cursor c = db.rawQuery("SELECT id, stock, buying_price FROM products", null);
        try {
            while (c.moveToNext()) {
                List<Object[]> l = ev.get(c.getLong(0));
                out.put(c.getLong(0), replay(c.getDouble(1), c.getDouble(2), l == null ? new ArrayList<Object[]>() : l, null, null));
            }
        } finally { c.close(); }
        return out;
    }
    private static void add(Map<Long, List<Object[]>> m, long id, Object[] e) { List<Object[]> l = m.get(id); if (l == null) { l = new ArrayList<>(); m.put(id, l); } l.add(e); }
    /** @param trace reçoit des lignes {date, libellé, réf, entrée, sortie, coût unit., CMP, stock} */
    static double replay(double stock, double buy, List<Object[]> events, List<double[]> traceNum, List<String[]> traceTxt) {
        Collections.sort(events, (a, b) -> { int x = ((String) a[0]).compareTo((String) b[0]); return x != 0 ? x : Integer.compare((Integer) a[4], (Integer) b[4]); });
        double net = 0; Double first = null;
        for (Object[] e : events) { int t = (Integer) e[1]; double q = (Double) e[2]; if (t == 1) net -= q; else { net += q; if (t == 0 && first == null) first = (Double) e[3]; } }
        double q = Math.max(0, stock - net), c = first != null ? first : buy;
        if (q > 0 && traceNum != null) { traceNum.add(new double[]{q, 0, c, Math.round(c * 10000.0) / 10000.0, q}); traceTxt.add(new String[]{events.isEmpty() ? "" : (String) events.get(0)[0], "Report (stock non tracé)", ""}); }
        for (Object[] e : events) {
            double nq = (Double) e[2]; int t = (Integer) e[1]; double in = 0, outq = 0, uc;
            if (t == 0) { double tt = q + nq; double cost = (Double) e[3]; c = tt > 0 ? (q * c + nq * cost) / tt : cost; q = tt; in = nq; uc = cost; }
            else if (t == 2) { q += nq; in = nq; uc = c; }
            else { q = Math.max(0, q - nq); outq = nq; uc = c; }
            if (traceNum != null) { traceNum.add(new double[]{in, outq, uc, Math.round(c * 10000.0) / 10000.0, q}); traceTxt.add(new String[]{(String) e[0], (String) e[5], (String) e[6]}); }
        }
        return Math.round(c * 10000.0) / 10000.0;
    }

    private static volatile boolean migrated = false;
    /** Une fois : crée le mouvement « stock initial » des produits existants (stock non tracé → valorisé exactement). */
    private void migrateOpenings() {
        if (migrated) return;
        synchronized (AccountingService.class) {
            if (migrated) return;
            if (!"1".equals(db.getConfig("stk_mig"))) {
                Map<Long, List<Object[]>> ev = events(false);
                Set<Long> has = new HashSet<>();
                Cursor c = db.rawQuery("SELECT DISTINCT product_id FROM stock_moves WHERE kind='opening'", null);
                try { while (c.moveToNext()) has.add(c.getLong(0)); } finally { c.close(); }
                List<Object[]> todo = new ArrayList<>();
                c = db.rawQuery("SELECT id, name, stock, buying_price FROM products", null);
                try {
                    while (c.moveToNext()) {
                        if (has.contains(c.getLong(0))) continue;
                        List<Object[]> l = ev.get(c.getLong(0)); double net = 0; Double first = null; String d = "9999";
                        if (l != null) {
                            Collections.sort(l, (a, b) -> { int x = ((String) a[0]).compareTo((String) b[0]); return x != 0 ? x : Integer.compare((Integer) a[4], (Integer) b[4]); });
                            for (Object[] e : l) { int t = (Integer) e[1]; net += t == 1 ? -(Double) e[2] : (Double) e[2]; if (t == 0 && first == null) first = (Double) e[3]; if (!((String) e[0]).isEmpty() && ((String) e[0]).compareTo(d) < 0) d = (String) e[0]; }
                        }
                        int q = (int) Math.max(0, c.getInt(2) - net); if (q <= 0) continue;
                        todo.add(new Object[]{c.getLong(0), c.getString(1), q, first != null ? first : c.getDouble(3), d.equals("9999") ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) : d});
                    }
                } finally { c.close(); }
                for (Object[] t : todo) rawMove((Long) t[0], (String) t[1], "opening", (Integer) t[2], (Double) t[3], "Stock initial", "", (String) t[4]);
                db.setConfig("stk_mig", "1");
            }
            migrated = true;
        }
    }
    /** Enregistre un mouvement au registre (ne modifie PAS le stock). */
    public long rawMove(long pid, String name, String kind, int qty, double cost, String reason, String note, String date) {
        if (qty <= 0) return -1;
        ContentValues cv = new ContentValues();
        cv.put("product_id", pid); cv.put("product_name", name); cv.put("kind", kind); cv.put("qty", qty); cv.put("unit_cost", Math.round(Math.max(0, cost) * 10000.0) / 10000.0);
        cv.put("move_date", date != null ? date : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())); cv.put("reason", reason == null ? "" : reason); cv.put("note", note == null ? "" : note);
        return db.getWritableDatabase().insert("stock_moves", null, cv);
    }
    /** Mouvement manuel : entrée (coût saisi) ou sortie (valorisée au CMP) ; le stock est mis à jour. @return null si OK, sinon message d'erreur */
    public String saveMove(long pid, boolean out, int qty, double cost, String reason, String note) {
        if (qty < 1) return I18n.t("Quantité invalide");
        android.database.sqlite.SQLiteDatabase q = db.getWritableDatabase();
        q.beginTransaction();
        try {
            Cursor c = q.rawQuery("SELECT name, stock, buying_price FROM products WHERE id=?", new String[]{String.valueOf(pid)});
            String name; int st; double buy;
            try { if (!c.moveToFirst()) return I18n.t("Produit introuvable"); name = c.getString(0); st = c.getInt(1); buy = c.getDouble(2); } finally { c.close(); }
            if (out && qty > st) return I18n.t("Stock insuffisant : ") + name + " (" + st + ")";
            double cm = out ? (cmpMap().containsKey(pid) ? cmpMap().get(pid) : buy) : cost;
            q.execSQL("UPDATE products SET stock=stock" + (out ? "-" : "+") + "? WHERE id=?", new Object[]{qty, pid});
            rawMove(pid, name, out ? "out" : "in", qty, cm, reason, note, null);
            q.setTransactionSuccessful(); return null;
        } finally { q.endTransaction(); }
    }
    /** Changement de stock via la fiche produit : trace d'un mouvement (ouverture à la création, correction ensuite). */
    public void traceProductChange(long pid, String name, int oldStock, int newStock, double buy, boolean created) {
        if (created) { if (newStock > 0) rawMove(pid, name, "opening", newStock, buy, "Stock initial", "", null); return; }
        if (oldStock == newStock) return;
        Double cm = cmpMap().get(pid);
        rawMove(pid, name, newStock > oldStock ? "in" : "out", Math.abs(newStock - oldStock), cm != null ? cm : buy, "Correction de stock", "", null);
    }

    /** Fiche de stock (pid>0) ou journal de tous les mouvements : date, produit, opération, entrée, sortie, coût, CMP, stock, valeur. */
    public ReportTable stockCard(long pid, String from, String to, String q) {
        ReportTable t = new ReportTable(pid > 0 ? I18n.t("Fiche de stock") : I18n.t("Mouvements de stock"), new String[]{I18n.t("Date"), I18n.t("Produit"), I18n.t("Opération"), I18n.t("Entrée"), I18n.t("Sortie"), "CMP", I18n.t("Stock"), I18n.t("Valeur stock")},
                new float[]{1.1f, 1.7f, 1.7f, 0.7f, 0.7f, 0.9f, 0.7f, 1.1f}, new boolean[]{false, false, false, true, true, true, true, true});
        Map<Long, List<Object[]>> ev = events(true);
        List<Object[]> rows = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id, name, stock, buying_price, barcode FROM products ORDER BY name COLLATE NOCASE", null);
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0); if (pid > 0 && id != pid) continue; if (pid <= 0 && !match(q, c.getString(1), c.getString(4))) continue;
                List<Object[]> l = ev.get(id); List<double[]> tn = new ArrayList<>(); List<String[]> tt = new ArrayList<>();
                replay(c.getDouble(2), c.getDouble(3), l == null ? new ArrayList<Object[]>() : l, tn, tt);
                for (int i = 0; i < tn.size(); i++) {
                    String d = d10(tt.get(i)[0]); if (!from.isEmpty() && d.compareTo(from) < 0) continue; if (!to.isEmpty() && d.compareTo(to) > 0) continue;
                    rows.add(new Object[]{tt.get(i)[0], c.getString(1), tt.get(i)[1], tn.get(i)});
                }
            }
        } finally { c.close(); }
        Collections.sort(rows, (a, b) -> ((String) a[0]).compareTo((String) b[0]));
        for (Object[] r : rows) {
            double[] x = (double[]) r[3];
            t.add(false, dshow(d10((String) r[0])), (String) r[1], I18n.t((String) r[2]), x[0] > 0 ? String.valueOf((long) x[0]) : "", x[1] > 0 ? String.valueOf((long) x[1]) : "", UiKit.fmt(x[3]), String.valueOf((long) x[4]), UiKit.fmt(x[4] * x[3]));
        }
        return t;
    }

    /** Inventaire valorisé : quantités en stock × CMP, valeur de vente, marge latente ; rapprochement avec le compte 311. */
    public ReportTable inventory(String q) {
        ReportTable t = new ReportTable(I18n.t("Inventaire valorisé (CMP)"), new String[]{I18n.t("Produit"), I18n.t("Qté"), "CMP", I18n.t("Valeur CMP"), I18n.t("Valeur vente")},
                new float[]{2.4f, 0.7f, 1f, 1.2f, 1.2f}, new boolean[]{false, true, true, true, true});
        Map<Long, Double> cmp = cmpMap();
        Cursor c = db.rawQuery("SELECT id, name, barcode, COALESCE(category,'Général'), stock, min_stock, buying_price, selling_price FROM products WHERE archived=0 ORDER BY 4, 2 COLLATE NOCASE", null);
        String cur = null; double gq = 0, gv = 0, gs = 0, tq = 0, tv = 0, ts = 0;
        try {
            while (c.moveToNext()) {
                if (!match(q, c.getString(1), c.getString(2), c.getString(3))) continue;
                String cat = c.getString(3);
                if (cur == null || !cur.equals(cat)) {
                    if (cur != null) t.add(true, I18n.t("Sous-total") + " " + cur, fmtN(gq), "", UiKit.fmt(gv), UiKit.fmt(gs));
                    t.add(true, cat, "", "", "", ""); cur = cat; gq = gv = gs = 0;
                }
                double cm = cmp.containsKey(c.getLong(0)) ? cmp.get(c.getLong(0)) : c.getDouble(6);
                int st = c.getInt(4); double v = st * cm, sv = st * c.getDouble(7);
                t.add(false, "   " + c.getString(1) + (st <= 0 ? " ⛔" : st <= c.getInt(5) ? " ⚠" : ""), String.valueOf(st), UiKit.fmt(cm), UiKit.fmt(v), UiKit.fmt(sv));
                gq += st; gv += v; gs += sv; tq += st; tv += v; ts += sv;
            }
        } finally { c.close(); }
        if (cur != null) t.add(true, I18n.t("Sous-total") + " " + cur, fmtN(gq), "", UiKit.fmt(gv), UiKit.fmt(gs));
        t.add(true, I18n.t("TOTAL INVENTAIRE"), fmtN(tq), "", UiKit.fmt(tv), UiKit.fmt(ts));
        t.add(true, I18n.t("Marge latente (vente − CMP)"), "", "", "", UiKit.fmt(ts - tv));
        double[] b = totals("", "").get(STOCK); double book = b == null ? 0 : b[0] - b[1];
        t.add(false, I18n.t("Solde comptable du compte 311"), "", "", UiKit.fmt(book), "");
        t.add(false, I18n.t("Écart inventaire − comptabilité"), "", "", UiKit.fmt(tv - book), "");
        return t;
    }
    private static String fmtN(double v) { return String.valueOf((long) v); }

    /** Historique des inventaires physiques (ids = id d'inventaire : toucher pour le détail). */
    public ReportTable counts(String from, String to) {
        ReportTable t = new ReportTable(I18n.t("Inventaires physiques (comptages)"), new String[]{I18n.t("Date"), I18n.t("Référence"), I18n.t("Art."), I18n.t("Écart qté"), I18n.t("Écart valeur")},
                new float[]{1.1f, 1.9f, 0.6f, 0.9f, 1.2f}, new boolean[]{false, false, true, true, true});
        List<String> a = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT v.id, v.inv_date, v.reference, COUNT(i.id), COALESCE(SUM(i.counted-i.theoretical),0), COALESCE(SUM((i.counted-i.theoretical)*i.cmp),0)"
                + " FROM inventories v LEFT JOIN inventory_items i ON i.inventory_id=v.id WHERE 1=1" + range("v.inv_date", from, to, a) + " GROUP BY v.id ORDER BY v.inv_date DESC, v.id DESC", a.toArray(new String[0]));
        try { while (c.moveToNext()) t.add(false, c.getLong(0), dshow(c.getString(1)), c.getString(2), String.valueOf(c.getInt(3)), String.valueOf(c.getLong(4)), UiKit.fmt(c.getDouble(5))); }
        finally { c.close(); }
        return t;
    }
    /** Détail d'un inventaire physique sous forme de tableau (export PDF). */
    public ReportTable countTable(long id) {
        String ref = ""; Cursor h = db.rawQuery("SELECT reference, inv_date, note FROM inventories WHERE id=?", new String[]{String.valueOf(id)});
        try { if (h.moveToFirst()) ref = h.getString(0) + " — " + dshow(h.getString(1)) + (h.getString(2) == null || h.getString(2).isEmpty() ? "" : " — " + h.getString(2)); } finally { h.close(); }
        ReportTable t = new ReportTable(I18n.t("Inventaire physique") + " " + ref, new String[]{I18n.t("Produit"), I18n.t("Théorique"), I18n.t("Compté"), I18n.t("Écart"), "CMP", I18n.t("Écart valeur")},
                new float[]{2.4f, 0.9f, 0.9f, 0.8f, 1f, 1.2f}, new boolean[]{false, true, true, true, true, true});
        double tv = 0; long tq = 0;
        Cursor c = db.rawQuery("SELECT product_name, theoretical, counted, cmp FROM inventory_items WHERE inventory_id=? ORDER BY product_name COLLATE NOCASE", new String[]{String.valueOf(id)});
        try { while (c.moveToNext()) { int dq = c.getInt(2) - c.getInt(1); double dv = dq * c.getDouble(3); tv += dv; tq += dq; t.add(false, c.getString(0), String.valueOf(c.getInt(1)), String.valueOf(c.getInt(2)), String.valueOf(dq), UiKit.fmt(c.getDouble(3)), UiKit.fmt(dv)); } } finally { c.close(); }
        t.add(true, "TOTAL", "", "", String.valueOf(tq), "", UiKit.fmt(tv));
        return t;
    }
    public String countDetail(long id) {
        StringBuilder sb = new StringBuilder(); double tv = 0;
        Cursor c = db.rawQuery("SELECT product_name, theoretical, counted, cmp FROM inventory_items WHERE inventory_id=? ORDER BY product_name COLLATE NOCASE", new String[]{String.valueOf(id)});
        try {
            while (c.moveToNext()) {
                int dq = c.getInt(2) - c.getInt(1); double dv = dq * c.getDouble(3); tv += dv;
                sb.append(c.getString(0)).append(" : ").append(c.getInt(1)).append(" → ").append(c.getInt(2)).append(" (").append(dq > 0 ? "+" : "").append(dq).append(")  ").append(UiKit.fmt(dv)).append("\n");
            }
        } finally { c.close(); }
        return sb.append("\n").append(I18n.t("Écart valorisé : ")).append(UiKit.xaf(tv)).toString();
    }

    /** Valide un comptage : stock ajusté aux quantités comptées (écart valorisé au CMP, écriture 6031/311 générée). @return id d'inventaire ou -1 */
    public long saveCount(String note, Map<Long, Integer> counted) {
        if (counted == null || counted.isEmpty()) return -1;
        Map<Long, Double> cmp = cmpMap();
        android.database.sqlite.SQLiteDatabase q = db.getWritableDatabase();
        q.beginTransaction();
        try {
            Date now = new Date();
            String ref = "INVT-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now);
            ContentValues h = new ContentValues();
            h.put("reference", ref); h.put("inv_date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)); h.put("note", note == null ? "" : note);
            h.put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now));
            long iid = q.insert("inventories", null, h); if (iid < 0) return -1;
            for (Map.Entry<Long, Integer> e : counted.entrySet()) {
                Cursor c = q.rawQuery("SELECT name, stock, buying_price FROM products WHERE id=?", new String[]{String.valueOf(e.getKey())});
                try {
                    if (!c.moveToFirst()) continue;
                    int cnt = Math.max(0, e.getValue());
                    ContentValues iv = new ContentValues();
                    iv.put("inventory_id", iid); iv.put("product_id", e.getKey()); iv.put("product_name", c.getString(0)); iv.put("theoretical", c.getInt(1)); iv.put("counted", cnt);
                    iv.put("cmp", cmp.containsKey(e.getKey()) ? cmp.get(e.getKey()) : c.getDouble(2));
                    q.insert("inventory_items", null, iv);
                    if (cnt != c.getInt(1)) q.execSQL("UPDATE products SET stock=? WHERE id=?", new Object[]{cnt, e.getKey()});
                } finally { c.close(); }
            }
            q.setTransactionSuccessful();
            return iid;
        } finally { q.endTransaction(); }
    }
}
