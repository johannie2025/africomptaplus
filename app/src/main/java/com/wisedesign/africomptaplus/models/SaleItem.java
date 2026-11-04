package com.wisedesign.africomptaplus.models;

public class SaleItem {
    public long   id;
    public long   saleId;
    public long   productId;
    public String productName;
    public double buyingPrice;
    public int    quantity;
    public double unitPrice;
    public double totalPrice;

    public SaleItem() {}

    public SaleItem(long productId, String productName, double buyingPrice,
                    int quantity, double unitPrice) {
        this.productId   = productId;
        this.productName = productName;
        this.buyingPrice = buyingPrice;
        this.quantity    = quantity;
        this.unitPrice   = unitPrice;
        this.totalPrice  = unitPrice * quantity;
    }
}
