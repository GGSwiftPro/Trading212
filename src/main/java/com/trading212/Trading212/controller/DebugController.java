package com.trading212.Trading212.controller;

import com.trading212.Trading212.model.CryptoPriceUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/debug")
public class DebugController {
    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);
    private final SimpMessagingTemplate messagingTemplate;

    public DebugController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/test-ws")
    public String testWebSocket() {
        try {
            CryptoPriceUpdate testUpdate = new CryptoPriceUpdate(
                "BTC",
                new BigDecimal("50000.00"),
                System.currentTimeMillis()
            );
            
            logger.info("Sending test WebSocket message: {}", testUpdate);
            messagingTemplate.convertAndSend("/topic/prices", testUpdate);
            return "Test message sent: " + testUpdate;
        } catch (Exception e) {
            logger.error("Error sending test message", e);
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/check-ws")
    public String checkWebSocket() {
        return "WebSocket test endpoint is available. Use /test-ws to send a test message.";
    }
}
