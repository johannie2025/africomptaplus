package com.wisedesign.africomptaplus.models;

import java.util.ArrayList;
import java.util.List;

public class PendingSale {
    public long id;
    public long clientId;
    public String clientName;
    public String clientPhone;
    public String paymentMethod;
    public String dueDate;
    public String discountType = "percent";
    public double discountValue;
    public double vatRate;
    public boolean vatEnabled;
    public String createdAt;
    public List<SaleItem> items = new ArrayList<>();
}
