package com.wisedesign.africomptaplus.models;

public class Product {
    public long   id;
    public String name;
    public String barcode;
    public double buyingPrice;
    public double sellingPrice;
    public int    stock;
    public int    minStock;

    public Product() {}

    public Product(long id, String name, String barcode,
                   double buyingPrice, double sellingPrice,
                   int stock, int minStock) {
        this.id           = id;
        this.name         = name;
        this.barcode      = barcode;
        this.buyingPrice  = buyingPrice;
        this.sellingPrice = sellingPrice;
        this.stock        = stock;
        this.minStock     = minStock;
    }

    public boolean isLowStock() {
        return stock <= minStock;
    }
}
