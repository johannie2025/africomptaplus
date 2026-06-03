package com.wisedesign.africomptaplus.models;

public class Expense {
    public long   id;
    public String category;
    public String description;
    public double amount;
    public String date;

    public Expense() {}

    public Expense(String category, String description, double amount, String date) {
        this.category    = category;
        this.description = description;
        this.amount      = amount;
        this.date        = date;
    }
}
