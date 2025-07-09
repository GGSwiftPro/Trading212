package com.trading212.Trading212.dto;

import java.math.BigDecimal;

public class ResetAccountResponse {
    private Long userId;
    private BigDecimal newBalance;
    private String message;

    public ResetAccountResponse() {
    }

    public ResetAccountResponse(Long userId, BigDecimal newBalance, String message) {
        this.userId = userId;
        this.newBalance = newBalance;
        this.message = message;
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public void setNewBalance(BigDecimal newBalance) {
        this.newBalance = newBalance;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
