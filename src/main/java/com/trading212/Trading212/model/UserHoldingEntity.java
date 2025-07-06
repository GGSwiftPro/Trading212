package com.trading212.Trading212.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserHoldingEntity {
    private Long id;
    private Long userId;
    private Long cryptoId;
    private BigDecimal quantity;
    private LocalDateTime lastUpdated;
    private String cryptoName;
    private String cryptoSymbol;
    private BigDecimal currentPrice;

    // Default constructor
    public UserHoldingEntity() {
    }

    // All-args constructor
    public UserHoldingEntity(Long id, Long userId, Long cryptoId, BigDecimal quantity, LocalDateTime lastUpdated) {
        this.id = id;
        this.userId = userId;
        this.cryptoId = cryptoId;
        this.quantity = quantity;
        this.lastUpdated = lastUpdated;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getCryptoName() {
        return cryptoName;
    }

    public void setCryptoName(String cryptoName) {
        this.cryptoName = cryptoName;
    }

    public String getCryptoSymbol() {
        return cryptoSymbol;
    }

    public void setCryptoSymbol(String cryptoSymbol) {
        this.cryptoSymbol = cryptoSymbol;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getTotalValue() {
        return quantity != null && currentPrice != null ? quantity.multiply(currentPrice) : BigDecimal.ZERO;
    }
}
