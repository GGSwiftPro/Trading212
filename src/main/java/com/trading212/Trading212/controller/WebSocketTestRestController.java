package com.trading212.Trading212.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for testing WebSocket functionality via HTTP endpoints.
 */
@RestController
@RequestMapping("/api/ws-test")
public class WebSocketTestRestController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTestRestController.class);
    
    private final WebSocketTestController webSocketTestController;

    @Autowired
    public WebSocketTestRestController(WebSocketTestController webSocketTestController) {
        this.webSocketTestController = webSocketTestController;
    }

    /**
     * Sends a broadcast message to all connected WebSocket clients
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcastMessage(@RequestParam String message) {
        logger.info("Received request to broadcast message: {}", message);
        
        webSocketTestController.broadcastMessage(message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Broadcast message sent: " + message);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Sends a private message to a specific user via WebSocket
     */
    @PostMapping("/private-message")
    public ResponseEntity<Map<String, Object>> sendPrivateMessage(
            @RequestParam String username,
            @RequestParam String message) {
        
        logger.info("Sending private message to user: {}, message: {}", username, message);
        
        webSocketTestController.sendPrivateMessage(username, message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Private message sent to " + username);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets information about active WebSocket connections
     */
    @GetMapping("/connection-info")
    public ResponseEntity<Map<String, Object>> getConnectionInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("activeConnections", webSocketTestController.getActiveSessionsCount());
        response.put("testMessageCount", webSocketTestController.getTestMessageCount());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Triggers a test price update for a specific symbol
     */
    @PostMapping("/trigger-price-update")
    public ResponseEntity<Map<String, Object>> triggerPriceUpdate(
            @RequestParam(defaultValue = "BTC/USD") String symbol) {
        
        logger.info("Triggering manual price update for symbol: {}", symbol);
        
        Map<String, Object> priceUpdate = webSocketTestController.createTestPriceUpdate(symbol);
        webSocketTestController.broadcastPriceUpdate(priceUpdate);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Price update triggered for " + symbol);
        response.put("priceUpdate", priceUpdate);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
