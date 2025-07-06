package com.trading212.Trading212.dto;

import java.math.BigDecimal;

public class TransactionRequest {
    private Long userId;
    private Long cryptoId;
    private String type; // "BUY" or "SELL"
    private BigDecimal quantity;
    
    // Default constructor
    public TransactionRequest() {
    }
    
    // All-args constructor
    public TransactionRequest(Long userId, Long cryptoId, String type, BigDecimal quantity) {
        this.userId = userId;
        this.cryptoId = cryptoId;
        this.type = type;
        this.quantity = quantity;
    }
    
    // Getters and setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Long getCryptoId() {
        return cryptoId;
    }
    
    public void setCryptoId(Long cryptoId) {
        this.cryptoId = cryptoId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
