package com.trading212.Trading212.controller;

import com.trading212.Trading212.model.CryptoCurrencyEntity;
import com.trading212.Trading212.service.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/crypto")
public class CryptoController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CryptoController.class);

    private final CryptoService cryptoService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CryptoController(CryptoService cryptoService, JdbcTemplate jdbcTemplate) {
        this.cryptoService = cryptoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<CryptoCurrencyEntity>> getAllCryptocurrencies() {
        List<CryptoCurrencyEntity> cryptos = cryptoService.getAllCryptocurrencies();
        return ResponseEntity.ok(cryptos);
    }
    
    @PostMapping("/init")
    @Transactional
    public ResponseEntity<String> initializeCryptocurrencies() {
        try {
            logger.info("Manually initializing cryptocurrency data...");
            List<CryptoCurrencyEntity> existing = cryptoService.getAllCryptocurrencies();
            if (!existing.isEmpty()) {
                return ResponseEntity.badRequest().body("Cryptocurrencies already exist in the database. Found: " + existing.size());
            }
            
            // This will trigger the initialization
            cryptoService.initializeCryptocurrencies();
            
            List<CryptoCurrencyEntity> cryptos = cryptoService.getAllCryptocurrencies();
            return ResponseEntity.ok("Successfully initialized " + cryptos.size() + " cryptocurrencies");
        } catch (Exception e) {
            logger.error("Failed to initialize cryptocurrencies: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Failed to initialize cryptocurrencies: " + e.getMessage());
        }
    }
    
    @GetMapping("/db-info")
    public ResponseEntity<Map<String, Object>> getDatabaseInfo() {
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
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("error_type", e.getClass().getName());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/test-db")
    public ResponseEntity<String> testDatabaseConnection() {
        try {
            logger.info("Testing database connection...");
            
            // Test basic connection
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            logger.info("Connected to database: {}", dbName);
            
            // Test table access
            List<CryptoCurrencyEntity> cryptos = cryptoService.getAllCryptocurrencies();
            logger.info("Successfully connected to database. Found {} cryptocurrencies.", cryptos.size());
            
            return ResponseEntity.ok("Database connection successful! Found " + cryptos.size() + " cryptocurrencies in database: " + dbName);
        } catch (Exception e) {
            String errorMsg = "Database connection failed: " + e.getMessage();
            logger.error(errorMsg, e);
            return ResponseEntity.internalServerError().body(errorMsg);
        }
    }
}
