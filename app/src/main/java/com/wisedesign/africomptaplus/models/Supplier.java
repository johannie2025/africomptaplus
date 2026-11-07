package com.wisedesign.africomptaplus.models;
public class Supplier {
    public long id;
    public String name = "", phone = "", email = "", address = "", notes = "";
    public double due;          // reste à payer (calculé)
    public int purchaseCount;   // nombre d'achats (calculé)
}
