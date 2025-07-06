package com.trading212.Trading212.model;

import java.math.BigDecimal;

public class CryptoPriceUpdate {
    private String symbol;
    private BigDecimal newPrice;
    private long timestamp;

    // No-argument constructor
    public CryptoPriceUpdate() {
    }

    // All-arguments constructor
    public CryptoPriceUpdate(String symbol, BigDecimal newPrice, long timestamp) {
        this.symbol = symbol;
        this.newPrice = newPrice;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public void setNewPrice(BigDecimal newPrice) {
        this.newPrice = newPrice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
