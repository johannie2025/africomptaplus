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

    // ── English ─────────────────────────────────────────────────────────────
    private static final String[] EU = {"zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve",
            "thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
    private static final String[] ET = {"","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"};
    private static String en3(int n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 100) { sb.append(EU[n / 100]).append(" hundred"); n %= 100; if (n > 0) sb.append(" "); }
        if (n >= 20) { sb.append(ET[n / 10]); if (n % 10 > 0) sb.append("-").append(EU[n % 10]); }
        else if (n > 0) sb.append(EU[n]);
        return sb.toString();
    }
    public static String en(long n) {
        if (n == 0) return "zero";
        if (n < 0) return "minus " + en(-n);
        String[] names = {"", " thousand", " million", " billion"};
        StringBuilder sb = new StringBuilder();
        int idx = 0; long m = n; java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        while (m > 0) { int g = (int) (m % 1000); if (g > 0) parts.add(0, en3(g) + names[idx]); m /= 1000; idx++; }
        return String.join(" ", parts);
    }

    // ── Español ─────────────────────────────────────────────────────────────
    private static final String[] SU = {"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce",
            "trece","catorce","quince","dieciséis","diecisiete","dieciocho","diecinueve","veinte","veintiuno","veintidós","veintitrés",
            "veinticuatro","veinticinco","veintiséis","veintisiete","veintiocho","veintinueve"};
    private static final String[] ST = {"","","","treinta","cuarenta","cincuenta","sesenta","setenta","ochenta","noventa"};
    private static final String[] SH = {"","ciento","doscientos","trescientos","cuatrocientos","quinientos","seiscientos","setecientos","ochocientos","novecientos"};
    private static String es3(int n) {
        if (n == 100) return "cien";
        StringBuilder sb = new StringBuilder();
        if (n >= 100) { sb.append(SH[n / 100]); n %= 100; if (n > 0) sb.append(" "); }
        if (n >= 30) { sb.append(ST[n / 10]); if (n % 10 > 0) sb.append(" y ").append(SU[n % 10]); }
        else if (n > 0 || sb.length() == 0) sb.append(SU[n]);
        return sb.toString();
    }
    private static String apocope(String s) {   // « uno » → « un » devant un nom (mil, millón, francos…)
        if (s.endsWith("veintiuno")) return s.substring(0, s.length() - 9) + "veintiún";
        if (s.endsWith(" uno") || s.equals("uno")) return s.substring(0, s.length() - 3) + "un";
        return s;
    }
    public static String es(long n) {
        if (n == 0) return "cero";
        if (n < 0) return "menos " + es(-n);
        StringBuilder sb = new StringBuilder();
        long mm = n / 1_000_000_000L; n %= 1_000_000_000L;
        long mi = n / 1_000_000L;     n %= 1_000_000L;
        long th = n / 1000L;          n %= 1000L;
        if (mm > 0) sb.append(mm == 1 ? "mil millones " : apocope(es3((int) mm)) + " mil millones ");
        if (mi > 0) sb.append(mi == 1 ? "un millón " : apocope(es3((int) mi)) + " millones ");
        if (th > 0) sb.append(th == 1 ? "mil " : apocope(es3((int) th)) + " mil ");
        if (n > 0) sb.append(es3((int) n));
        return sb.toString().trim();
    }

    public static String currencyName(String code) {
        String c = code == null ? "" : code.toUpperCase();
        String lg = com.wisedesign.africomptaplus.ui.I18n.lang();
        boolean en = "en".equals(lg), es = "es".equals(lg);
        switch (c) {
            case "XAF": case "XOF": case "FCFA": case "": return en ? "CFA francs" : es ? "francos CFA" : "francs CFA";
            case "GNF": return en ? "Guinean francs" : es ? "francos guineanos" : "francs guinéens";
            case "CDF": return en ? "Congolese francs" : es ? "francos congoleños" : "francs congolais";
            case "KMF": return en ? "Comorian francs" : es ? "francos comorenses" : "francs comoriens";
            case "EUR": return en ? "euros" : "euros";
            case "USD": return en ? "US dollars" : es ? "dólares estadounidenses" : "dollars US";
            default: return code;
        }
    }

    /** Ex. « Mille deux cents francs CFA ». */
    public static String amount(double v, String currencyCode) {
        String lg = com.wisedesign.africomptaplus.ui.I18n.lang();
        long r = Math.round(v);
        String w = ("en".equals(lg) ? en(r) : "es".equals(lg) ? apocope(es(r)) : fr(r)) + " " + currencyName(currencyCode);
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }
}
