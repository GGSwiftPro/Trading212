package com.trading212.Trading212.model;


import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class UserEntity {

    private Long id; // Corresponds to SERIAL PRIMARY KEY in PostgreSQL/MySQL (auto-incrementing)
    private String username; // Corresponds to VARCHAR(20) UNIQUE NOT NULL
    private BigDecimal balance; // Corresponds to DECIMAL(20, 8) NOT NULL DEFAULT 0.00
    private LocalDateTime lastUpdated; // Corresponds to TIMESTAMP DEFAULT CURRENT_TIMESTAMP



}
