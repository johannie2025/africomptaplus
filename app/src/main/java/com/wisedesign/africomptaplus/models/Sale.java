package com.wisedesign.africomptaplus.models;
import java.util.ArrayList;
import java.util.List;
public class Sale {
    public long   id;
    public String invoiceNumber;
    public long   clientId    = 0;
    public String clientName  = "Client Passager";
    public String clientPhone = "";
    public double totalAmount;
    public String paymentMethod;
    public String createdAt;
    public List<SaleItem> items = new ArrayList<>();
}
