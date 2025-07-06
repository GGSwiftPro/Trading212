package com.trading212.Trading212.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserEntity {

    private Long id; // Corresponds to SERIAL PRIMARY KEY in PostgreSQL/MySQL (auto-incrementing)
    private String username; // Corresponds to VARCHAR(50) UNIQUE NOT NULL
    private BigDecimal balance; // Corresponds to DECIMAL(20, 8) NOT NULL DEFAULT 10000.00
    private LocalDateTime lastUpdated; // Corresponds to TIMESTAMP DEFAULT CURRENT_TIMESTAMP

    public UserEntity(String username, BigDecimal balance) {
        this.username = username;
        this.balance = balance;
    }

    public UserEntity(String username) {
        this.username = username;
        this.balance = new BigDecimal("10000.00");
    }

    // No-argument constructor
    public UserEntity() {
    }

    // All-arguments constructor
    public UserEntity(Long id, String username, BigDecimal balance, LocalDateTime lastUpdated) {
        this.id = id;
        this.username = username;
        this.balance = balance;
        this.lastUpdated = lastUpdated;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
