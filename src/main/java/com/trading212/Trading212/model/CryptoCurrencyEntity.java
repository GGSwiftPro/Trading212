package com.trading212.Trading212.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CryptoCurrencyEntity {

    private Long id;
    private String symbol;
    private String name;
    private String krakenPairName;
    private BigDecimal currentPrice;
    private LocalDateTime lastUpdated;
    private Integer marketRank;

    // No-argument constructor
    public CryptoCurrencyEntity() {
    }

    public CryptoCurrencyEntity(String symbol, String name, String krakenPairName, BigDecimal currentPrice) {
        this.symbol = symbol;
        this.name = name;
        this.krakenPairName = krakenPairName;
        this.currentPrice = currentPrice;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKrakenPairName() {
        return krakenPairName;
    }

    public void setKrakenPairName(String krakenPairName) {
        this.krakenPairName = krakenPairName;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Integer getMarketRank() {
        return marketRank;
    }

    public void setMarketRank(Integer marketRank) {
        this.marketRank = marketRank;
    }
}