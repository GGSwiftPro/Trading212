package com.trading212.Trading212.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.util.Objects;

public class CryptoPriceUpdate {
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("newPrice")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal newPrice;
    
    @JsonProperty("timestamp")
    private long timestamp;

    // No-argument constructor for JSON deserialization
    public CryptoPriceUpdate() {
    }

    // All-arguments constructor
    @JsonCreator
    public CryptoPriceUpdate(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("newPrice") BigDecimal newPrice,
            @JsonProperty("timestamp") long timestamp) {
        this.symbol = symbol;
        this.newPrice = newPrice != null ? newPrice : BigDecimal.ZERO;
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
    
    @Override
    public String toString() {
        return "CryptoPriceUpdate{" +
                "symbol='" + symbol + '\'' +
                ", newPrice=" + newPrice +
                ", timestamp=" + timestamp +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CryptoPriceUpdate that = (CryptoPriceUpdate) o;
        return timestamp == that.timestamp &&
               Objects.equals(symbol, that.symbol) &&
               (newPrice == null ? that.newPrice == null : newPrice.compareTo(that.newPrice) == 0);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(symbol, newPrice, timestamp);
    }
}
