package com.trading212.Trading212.dto;

import com.trading212.Trading212.model.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionHistoryDTO {
    private Long id;
    private String symbol;
    private String cryptoName;
    private TransactionType type;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private BigDecimal profitLoss;
    private LocalDateTime timestamp;
    private String status;
}
