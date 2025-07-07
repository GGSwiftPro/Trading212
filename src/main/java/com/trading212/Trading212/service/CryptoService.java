package com.trading212.Trading212.service;

import com.trading212.Trading212.model.CryptoCurrencyEntity;
import com.trading212.Trading212.model.CryptoPriceUpdate;
import com.trading212.Trading212.repository.CryptoRepository;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class CryptoService {
    
    @Autowired
    public CryptoService(CryptoRepository cryptoRepo, SimpMessagingTemplate messagingTemplate) {
        this.cryptoRepo = cryptoRepo;
        this.messagingTemplate = messagingTemplate;
    }

    private static final Logger logger = LoggerFactory.getLogger(CryptoService.class);
    private final CryptoRepository cryptoRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, BigDecimal> lastPrices = new HashMap<>();

    @PostConstruct
    public void init() {
        initializeCryptocurrencies();
    }

    @Transactional
    public void initializeCryptocurrencies() {
        try {
            logger.info("Starting cryptocurrency data initialization...");

            // Ensure the table exists
            cryptoRepo.ensureTableExists();

            // List of cryptocurrencies to initialize with empty prices
            List<CryptoCurrencyEntity> cryptos = Arrays.asList(
                new CryptoCurrencyEntity("BTC", "Bitcoin", "XBT/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("ETH", "Ethereum", "ETH/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("ADA", "Cardano", "ADA/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("XRP", "Ripple", "XRP/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("LTC", "Litecoin", "LTC/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("BCH", "Bitcoin Cash", "BCH/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("DOT", "Polkadot", "DOT/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("LINK", "Chainlink", "LINK/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("SOL", "Solana", "SOL/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("UNI", "Uniswap", "UNI/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("DOGE", "Dogecoin", "DOGE/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("TRX", "TRON", "TRX/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("ETC", "Ethereum Classic", "ETC/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("XLM", "Stellar", "XLM/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("EOS", "EOS", "EOS/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("XTZ", "Tezos", "XTZ/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("ATOM", "Cosmos", "ATOM/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("FIL", "Filecoin", "FIL/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("VET", "VeChain", "VET/USD", BigDecimal.ZERO),
                new CryptoCurrencyEntity("NEO", "NEO", "NEO/USD", BigDecimal.ZERO)
            );

            // Get existing symbols
            Set<String> existingSymbols = cryptoRepo.findAll().stream()
                .map(c -> c.getSymbol().toUpperCase())
                .collect(Collectors.toSet());

            // Insert only new cryptocurrencies
            int inserted = 0;
            for (CryptoCurrencyEntity crypto : cryptos) {
                if (!existingSymbols.contains(crypto.getSymbol().toUpperCase())) {
                    try {
                        logger.info("Inserting new cryptocurrency: {} - {}", crypto.getSymbol(), crypto.getName());
                        cryptoRepo.insert(
                            crypto.getSymbol(),
                            crypto.getName(),
                            crypto.getKrakenPairName(),
                            crypto.getCurrentPrice().doubleValue()
                        );
                        inserted++;
                    } catch (Exception e) {
                        logger.error("Error inserting {}: {}", crypto.getSymbol(), e.getMessage());
                    }
                }
            }

            logger.info("Initialized {} cryptocurrencies", cryptos.size());
            if (inserted > 0) {
                logger.info("Successfully inserted {} new cryptocurrencies", inserted);
            }
        } catch (Exception e) {
            logger.error("Error initializing cryptocurrency data: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize cryptocurrency data", e);
        }
    }

    @Transactional
    public void updatePrice(String symbol, BigDecimal newPrice) {
        if (symbol == null || newPrice == null) {
            logger.warn("Attempted to update price with null symbol or price");
            return;
        }
        
        // Only update and notify if price has changed
        if (!newPrice.equals(lastPrices.get(symbol))) {
            try {
                // Update the price in the database
                cryptoRepo.updatePrice(symbol, newPrice);
                logger.debug("Updated price for {} to {}", symbol, newPrice);
                lastPrices.put(symbol, newPrice);
                
                // Create and broadcast the price update
                CryptoPriceUpdate update = new CryptoPriceUpdate(
                    symbol, 
                    newPrice, 
                    Instant.now().toEpochMilli()
                );
                
                // Send the update via WebSocket
                messagingTemplate.convertAndSend("/topic/prices", update);
                logger.trace("Broadcasted price update: {}", update);
            } catch (Exception e) {
                logger.error("Failed to process price update for {}: {}", symbol, e.getMessage());
            }
        } else {
            logger.trace("Price for {} unchanged: {}", symbol, newPrice);
        }
    }

    // This method is no longer needed as we're using Kraken WebSocket for real-time updates
//    @Scheduled(fixedRate = 5000) // Update every 5 seconds
//    public void simulatePriceUpdates() {
//        // In a real app, you would fetch actual prices from an API here
//        // This is just a simulation
//        List<CryptoCurrencyEntity> allCryptos = cryptoRepo.findAll();
//        for (CryptoCurrencyEntity crypto : allCryptos) {
//            BigDecimal currentPrice = crypto.getCurrentPrice();
//            // Simulate small price changes
//            double change = (Math.random() * 0.02) - 0.01; // -1% to +1%
//            BigDecimal newPrice = currentPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(change)));
//            updatePrice(crypto.getSymbol(), newPrice);
//        }
//    }

    // Helper method to insert a single cryptocurrency with error handling
    private void insertCrypto(String symbol, String name, String pair, double price) {
        try {
            logger.debug("Inserting {} - {} ({})", symbol, name, price);
            cryptoRepo.insert(symbol, name, pair, price);
        } catch (Exception e) {
            logger.error("Failed to insert {}: {}", symbol, e.getMessage());
            throw e; // Re-throw to trigger transaction rollback
        }
    }

    public List<CryptoCurrencyEntity> getAllCryptocurrencies() {
        return cryptoRepo.findAll();
    }

    public Optional<CryptoCurrencyEntity> getCryptocurrencyByKrakenPair(String krakenPairName) {
        return cryptoRepo.findByKrakenPairName(krakenPairName);
    }
}
