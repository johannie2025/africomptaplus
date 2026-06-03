package com.wisedesign.africomptaplus.models;

import java.util.ArrayList;
import java.util.List;

public class Sale {
    public long   id;
    public String invoiceNumber;
    public double totalAmount;
    public String paymentMethod;
    public String createdAt;
    public List<SaleItem> items = new ArrayList<>();

    public Sale() {}
}
