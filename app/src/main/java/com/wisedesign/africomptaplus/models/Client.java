package com.wisedesign.africomptaplus.models;
public class Client {
    public long   id;
    public String name    = "Client Passager";
    public String phone   = "";
    public String email   = "";
    public String address = "";
    public String notes   = "";
    public String createdAt;
    public Client() {}
    public Client(String name, String phone) { this.name = name; this.phone = phone; }
    public boolean isPassenger() { return id == 0 || "Client Passager".equals(name); }
    @Override public String toString() { return name + (phone != null && !phone.isEmpty() ? " · " + phone : ""); }
}
