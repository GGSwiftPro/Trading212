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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crypto")
public class CryptoController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CryptoController.class);

    private final CryptoService cryptoService;

    @Autowired
    public CryptoController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
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
        try {
            return ResponseEntity.ok(cryptoService.getDatabaseInfo());
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("error_type", e.getClass().getName());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    @GetMapping("/test-db")
    public ResponseEntity<String> testDatabaseConnection() {
        try {
            String result = cryptoService.testDatabaseConnection();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            String errorMsg = "Database connection failed: " + e.getMessage();
            logger.error(errorMsg, e);
            return ResponseEntity.internalServerError().body(errorMsg);
        }
    }
}
