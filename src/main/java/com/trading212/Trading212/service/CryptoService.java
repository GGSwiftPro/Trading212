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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class CryptoService {
    
    @Autowired
    public CryptoService(CryptoRepository cryptoRepo, SimpMessagingTemplate messagingTemplate, JdbcTemplate jdbcTemplate) {
        this.cryptoRepo = cryptoRepo;
        this.messagingTemplate = messagingTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final Logger logger = LoggerFactory.getLogger(CryptoService.class);
    private final CryptoRepository cryptoRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, BigDecimal> lastPrices = new HashMap<>();
    private final JdbcTemplate jdbcTemplate;

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
        // Only update and notify if price has changed
        if (!newPrice.equals(lastPrices.get(symbol))) {
            cryptoRepo.updatePrice(symbol, newPrice);
            lastPrices.put(symbol, newPrice);
            
            // Send update via WebSocket
            CryptoPriceUpdate update = new CryptoPriceUpdate(
                symbol, 
                newPrice, 
                Instant.now().toEpochMilli()
            );
            messagingTemplate.convertAndSend("/topic/prices", update);
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
    
    public Map<String, Object> getDatabaseInfo() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get database name
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            response.put("database", dbName);
            
            // Get database version
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            response.put("version", version);
            
            // List all tables
            List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", 
                String.class
            );
            response.put("tables", tables);
            
            // Check if cryptocurrencies table exists
            if (tables.contains("cryptocurrencies")) {
                // Get table structure
                List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SHOW COLUMNS FROM cryptocurrencies"
                );
                response.put("cryptocurrencies_columns", columns);
                
                // Get row count
                int count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cryptocurrencies", Integer.class);
                response.put("cryptocurrencies_count", count);
                
                // Get sample data if any
                if (count > 0) {
                    List<Map<String, Object>> sampleData = jdbcTemplate.queryForList(
                        "SELECT * FROM cryptocurrencies LIMIT 5");
                    response.put("cryptocurrencies_sample", sampleData);
                }
            }
            
            return response;
            
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("error_type", e.getClass().getName());
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    public String testDatabaseConnection() {
        try {
            logger.info("Testing database connection...");
            
            // Test basic connection
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            logger.info("Connected to database: {}", dbName);
            
            // Test table access
            List<CryptoCurrencyEntity> cryptos = getAllCryptocurrencies();
            logger.info("Successfully connected to database. Found {} cryptocurrencies.", cryptos.size());
            
            return "Database connection successful! Found " + cryptos.size() + " cryptocurrencies in database: " + dbName;
        } catch (Exception e) {
            String errorMsg = "Database connection failed: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public Optional<CryptoCurrencyEntity> getCryptocurrencyByKrakenPair(String krakenPairName) {
        return cryptoRepo.findByKrakenPairName(krakenPairName);
    }
}
