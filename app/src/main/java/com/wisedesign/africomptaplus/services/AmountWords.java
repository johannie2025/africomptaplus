package com.wisedesign.africomptaplus.services;

/** Montants en lettres (français) pour factures, devis, proforma et reçus. */
public final class AmountWords {
    private AmountWords() {}
    private static final String[] U = {"zéro","un","deux","trois","quatre","cinq","six","sept","huit","neuf","dix",
            "onze","douze","treize","quatorze","quinze","seize","dix-sept","dix-huit","dix-neuf"};
    private static final String[] T = {"","","vingt","trente","quarante","cinquante","soixante"};

    public static String fr(long n) {
        if (n == 0) return "zéro";
        if (n < 0) return "moins " + fr(-n);
        StringBuilder sb = new StringBuilder();
        long milliards = n / 1_000_000_000L; n %= 1_000_000_000L;
        long millions  = n / 1_000_000L;     n %= 1_000_000L;
        long milliers  = n / 1000L;          n %= 1000L;
        if (milliards > 0) sb.append(below1000((int) milliards, false)).append(milliards > 1 ? " milliards " : " milliard ");
        if (millions  > 0) sb.append(below1000((int) millions, false)).append(millions > 1 ? " millions " : " million ");
        if (milliers  > 0) sb.append(milliers == 1 ? "mille " : below1000((int) milliers, false) + " mille ");
        if (n > 0) sb.append(below1000((int) n, true));
        return sb.toString().trim();
    }

    private static String below100(int n, boolean last) {
        if (n < 20) return U[n];
        int t = n / 10, u = n % 10;
        if (t <= 6) {
            if (u == 0) return T[t];
            return T[t] + (u == 1 ? " et un" : "-" + U[u]);
        }
        if (t == 7) return "soixante" + (u == 1 ? " et onze" : "-" + U[10 + u]);
        if (n == 80) return last ? "quatre-vingts" : "quatre-vingt";
        return "quatre-vingt-" + U[n - 80];
    }

    private static String below1000(int n, boolean last) {
        int h = n / 100, r = n % 100;
        if (h == 0) return below100(r, last);
        String head = h == 1 ? "cent" : U[h] + " cent";
        if (r == 0) return (h > 1 && last) ? head + "s" : head;
        return head + " " + below100(r, last);
    }

    public static String currencyName(String code) {
        if (code == null) return "francs CFA";
        switch (code.toUpperCase()) {
            case "XAF": case "XOF": case "FCFA": case "": return "francs CFA";
            case "GNF": return "francs guinéens";
            case "CDF": return "francs congolais";
            case "KMF": return "francs comoriens";
            case "EUR": return "euros";
            case "USD": return "dollars US";
            default: return code;
        }
    }

    /** Ex. « Mille deux cents francs CFA ». */
    public static String amount(double v, String currencyCode) {
        String w = fr(Math.round(v)) + " " + currencyName(currencyCode);
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }
}
