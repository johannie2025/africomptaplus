package com.wisedesign.africomptaplus.models;
import java.util.ArrayList;
import java.util.List;
public class Purchase {
    public long id, supplierId;
    public String reference, supplierName, paymentMethod = "cash", notes = "", createdAt;
    public double total, initialPaid, paid;
    public List<SaleItem> items = new ArrayList<>();   // quantity, unitPrice = coût unitaire
    public double remaining() { return Math.max(0, total - paid); }
}
