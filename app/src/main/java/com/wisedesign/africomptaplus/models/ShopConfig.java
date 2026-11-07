package com.wisedesign.africomptaplus.models;

import com.wisedesign.africomptaplus.db.DatabaseHelper;

/** Identité de la boutique, affichée sur les factures et rapports PDF. */
public class ShopConfig {
    public String shopName    = "Ma Boutique";
    public String tagline     = "";
    public String shopPhone   = "";
    public String shopEmail   = "";
    public String shopAddress = "";
    public String taxId       = "";
    public String taxLabel    = "N° fiscal";
    public String logoPath    = "";
    public String currency    = "XAF";
    public String invoiceFooter = "Merci pour votre achat !";
    public boolean cloudBackupEnabled = false;
    public String cloudBackupToken = "";

    public static ShopConfig load(DatabaseHelper db) {
        ShopConfig c = new ShopConfig();
        c.shopName    = db.getConfig("shop_name");
        c.tagline     = db.getConfig("shop_tagline");
        c.shopPhone   = db.getConfig("shop_phone");
        c.shopEmail   = db.getConfig("shop_email");
        c.shopAddress = db.getConfig("shop_address");
        c.taxId       = db.getConfig("shop_tax_id");
        c.logoPath    = db.getConfig("shop_logo_path");
        String tl = db.getConfig("shop_tax_label");
        if (tl != null && !tl.isEmpty()) c.taxLabel = tl;
        String cur = db.getConfig("shop_currency");
        if (cur != null && !cur.isEmpty()) c.currency = cur;
        c.invoiceFooter = db.getConfig("invoice_footer");
        if (c.shopName.isEmpty()) c.shopName = "Ma Boutique";
        if (c.invoiceFooter.isEmpty()) c.invoiceFooter = "Merci pour votre achat !";
        return c;
    }
}
