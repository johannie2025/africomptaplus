package com.wisedesign.africomptaplus.models;
public class DebtPayment {
    public long   id;
    public long   debtId;
    public double amount;
    public String method;   // cash | mobile_money
    public String note;
    public String createdAt;
}
