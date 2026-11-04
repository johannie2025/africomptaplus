package com.wisedesign.africomptaplus.models;
public class Debt {
    public long   id;
    public long   clientId;
    public String clientName;
    public long   saleId;
    public double amount;
    public double paidAmount;
    public double remainingAmount() { return amount - paidAmount; }
    public String dueDate;
    public String status; // open | partial | paid
    public String notes;
    public String createdAt;
    public String updatedAt;
    public boolean isFullyPaid() { return paidAmount >= amount; }
}
