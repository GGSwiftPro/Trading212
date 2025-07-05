package com.trading212.Trading212.model;

import lombok.Getter;
import lombok.Setter; // Add @Setter for updating price
import lombok.NoArgsConstructor; // Add no-arg constructor
import lombok.AllArgsConstructor; // Add all-args constructor

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter // Needed for setting ID after insert and updating price
@NoArgsConstructor // Needed for JdbcTemplate row mapping and general use
@AllArgsConstructor // Useful for creating instances easily
public class CryptoCurrencyEntity {

    private Long id;
    private String symbol; // e.g., "BTC", "ETH"
    private String name; // e.g., "Bitcoin", "Ethereum"
    private String krakenPairName; // e.g., "XBT/USD", "ETH/USD" - crucial for Kraken API
    private BigDecimal currentPrice;
    private LocalDateTime lastUpdated;

    public CryptoCurrencyEntity(String symbol, String name, String krakenPairName, BigDecimal currentPrice) {
        this.symbol = symbol;
        this.name = name;
        this.krakenPairName = krakenPairName;
        this.currentPrice = currentPrice;
        // lastUpdated will be handled by DB default or set on retrieval
    }
}
