package com.trading212.Trading212.controller;

import com.trading212.Trading212.dto.TradeRequest;
import com.trading212.Trading212.dto.TradeResponse;
import com.trading212.Trading212.service.TradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/trade")
public class TradingController {
    private static final Logger logger = LoggerFactory.getLogger(TradingController.class);
    
    private final TradingService tradingService;

    @Autowired
    public TradingController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/buy")
    public ResponseEntity<TradeResponse> buyCrypto(@Valid @RequestBody TradeRequest request) {
        logger.info("Received BUY request: {}", request);
        try {
            TradeResponse response = tradingService.buyCrypto(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid buy request: {}", e.getMessage());
            TradeResponse errorResponse = new TradeResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (IllegalStateException e) {
            logger.error("Cannot process buy order: {}", e.getMessage());
            TradeResponse errorResponse = new TradeResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Error processing buy order: {}", e.getMessage(), e);
            TradeResponse errorResponse = new TradeResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage("An unexpected error occurred");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<TradeResponse> sellCrypto(@Valid @RequestBody TradeRequest request) {
        logger.info("Received SELL request: {}", request);
        try {
            TradeResponse response = tradingService.sellCrypto(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid sell request: {}", e.getMessage());
            TradeResponse errorResponse = new TradeResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (IllegalStateException e) {
            logger.error("Cannot process sell order: {}", e.getMessage());
            TradeResponse errorResponse = new TradeResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Error processing sell order: {}", e.getMessage(), e);
            TradeResponse errorResponse = new TradeResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage("An unexpected error occurred");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @GetMapping("/history/{userId}")
    public ResponseEntity<?> getTransactionHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        if (page < 1) {
            return ResponseEntity.badRequest().body("Page number must be greater than 0");
        }
        
        if (size < 1 || size > 100) {
            return ResponseEntity.badRequest().body("Page size must be between 1 and 100");
        }
        
        try {
            Map<String, Object> response = tradingService.getTransactionHistory(userId, page, size);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching transaction history: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error fetching transaction history");
        }
    }
}
