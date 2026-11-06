package com.wisedesign.africomptaplus.models;
import java.util.ArrayList;
import java.util.List;
/** Devis ou facture proforma (aucun impact sur le stock tant qu'il n'est pas converti en facture). */
public class Document {
    public static final String QUOTE = "quote", PROFORMA = "proforma";
    public long   id;
    public String docType = QUOTE;
    public String number;
    public long   clientId = 0;
    public String clientName = "Client Passager";
    public String clientPhone = "";
    public double subtotal, taxRate, taxAmount, totalAmount;
    public String validUntil = "";
    public String status = "open";      // open | converted
    public long   convertedSaleId;
    public String createdAt;
    public List<SaleItem> items = new ArrayList<>();
    public String label() { return PROFORMA.equals(docType) ? "Facture proforma" : "Devis"; }
}
