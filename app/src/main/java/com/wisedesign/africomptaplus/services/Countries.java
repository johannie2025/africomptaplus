package com.wisedesign.africomptaplus.services;

import android.content.Context;

import com.wisedesign.africomptaplus.db.DatabaseHelper;
import com.wisedesign.africomptaplus.ui.UiKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Profils des 17 États membres de l'OHADA : devise, TVA standard (indicative), libellé de l'identifiant fiscal,
 * organisme social. « Charger » applique le profil à la boutique et ajoute les comptes spécifiques au plan comptable.
 * Les taux de TVA sont des valeurs par défaut modifiables : à confirmer auprès de l'administration fiscale.
 */
public final class Countries {
    private Countries() {}

    public static class Country {
        public final String code, name, zone, currency, taxIdLabel, social;
        public final double vat;
        Country(String code, String name, String zone, String currency, double vat, String taxIdLabel, String social) {
            this.code = code; this.name = name; this.zone = zone; this.currency = currency;
            this.vat = vat; this.taxIdLabel = taxIdLabel; this.social = social;
        }
        @Override public String toString() { return name; }
    }

    private static final List<Country> ALL = new ArrayList<>();
    static {
        ALL.add(new Country("BJ", "Bénin", "UEMOA", "XOF", 18, "IFU", "CNSS"));
        ALL.add(new Country("BF", "Burkina Faso", "UEMOA", "XOF", 18, "IFU", "CNSS"));
        ALL.add(new Country("CM", "Cameroun", "CEMAC", "XAF", 19.25, "NIU", "CNPS"));
        ALL.add(new Country("CF", "Centrafrique", "CEMAC", "XAF", 19, "NIF", "CNSS"));
        ALL.add(new Country("KM", "Comores", "Autre", "KMF", 10, "NIF", "Organisme social"));
        ALL.add(new Country("CG", "Congo", "CEMAC", "XAF", 18.9, "NIU", "CNSS"));
        ALL.add(new Country("CI", "Côte d'Ivoire", "UEMOA", "XOF", 18, "NCC", "CNPS"));
        ALL.add(new Country("GA", "Gabon", "CEMAC", "XAF", 18, "NIF", "CNSS"));
        ALL.add(new Country("GN", "Guinée", "Autre", "GNF", 18, "NIF", "CNSS"));
        ALL.add(new Country("GW", "Guinée-Bissau", "UEMOA", "XOF", 18, "NIF", "INSS"));
        ALL.add(new Country("GQ", "Guinée équatoriale", "CEMAC", "XAF", 15, "NIF", "INSESO"));
        ALL.add(new Country("ML", "Mali", "UEMOA", "XOF", 18, "NIF", "INPS"));
        ALL.add(new Country("NE", "Niger", "UEMOA", "XOF", 19, "NIF", "CNSS"));
        ALL.add(new Country("CD", "RD Congo", "Autre", "CDF", 16, "NIF", "INSS"));
        ALL.add(new Country("SN", "Sénégal", "UEMOA", "XOF", 18, "NINEA", "IPRES / CSS"));
        ALL.add(new Country("TD", "Tchad", "CEMAC", "XAF", 18, "NIF", "CNPS"));
        ALL.add(new Country("TG", "Togo", "UEMOA", "XOF", 18, "NIF", "CNSS"));
    }

    public static List<Country> all() { return ALL; }
    public static Country byCode(String code) {
        for (Country c : ALL) if (c.code.equals(code)) return c;
        return null;
    }
    public static String vatText(double v) { return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v); }

    /** Comptes ajoutés au plan : [code, intitulé]. */
    public static String[][] accounts(Country c) {
        String v = vatText(c.vat);
        return new String[][]{
                {"4431", "TVA facturée sur ventes (" + v + " %) — " + c.name},
                {"4452", "TVA récupérable sur achats (" + v + " %) — " + c.name},
                {"4311", c.social + " — cotisations sociales à payer"},
                {"6641", "Cotisations " + c.social + " (part patronale)"},
                {"6412", "Patentes, licences et taxes professionnelles — " + c.name},
                {"6413", "Impôts et taxes locaux — " + c.name},
        };
    }

    public static String summary(Country c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Zone : ").append(c.zone).append("\n");
        sb.append("Devise : ").append(c.currency).append("\n");
        sb.append("TVA par défaut : ").append(vatText(c.vat)).append(" % (indicatif)\n");
        sb.append("Identifiant fiscal : ").append(c.taxIdLabel).append("\n");
        sb.append("Organisme social : ").append(c.social).append("\n");
        sb.append("Comptes ajoutés au plan : ").append(accounts(c).length);
        return sb.toString();
    }

    /** Applique le profil : devise, taux de TVA, libellé fiscal, comptes spécifiques. */
    public static void apply(Context ctx, Country c) {
        DatabaseHelper db = DatabaseHelper.getInstance(ctx);
        db.setConfig("shop_country", c.code);
        db.setConfig("shop_currency", c.currency);
        db.setConfig("tva_rate", vatText(c.vat));
        db.setConfig("shop_tax_label", c.taxIdLabel);
        AccountingService acc = new AccountingService(ctx);
        for (String[] a : accounts(c)) acc.addAccount(a[0], a[1]);
        UiKit.currency = c.currency;
    }
}
