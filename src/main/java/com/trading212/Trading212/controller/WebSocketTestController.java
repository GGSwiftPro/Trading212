package com.trading212.Trading212.controller;

import com.trading212.Trading212.model.CryptoPriceUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;

@RestController
@RequestMapping("/api/ws-test")
public class WebSocketTestController {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketTestController.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    public WebSocketTestController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Test endpoint to send a WebSocket message
     */
    @GetMapping("/send")
    public String sendTestMessage() {
        String[] symbols = {"BTC", "ETH", "ADA", "XRP", "LTC"};
        String symbol = symbols[random.nextInt(symbols.length)];
        BigDecimal price = BigDecimal.valueOf(1000 + random.nextDouble() * 9000);
        
        CryptoPriceUpdate update = new CryptoPriceUpdate(
            symbol,
            price,
            Instant.now().toEpochMilli()
        );
        
        logger.info("Sending test message: {}", update);
        messagingTemplate.convertAndSend("/topic/prices", update);
        
        return "Sent test message: " + update;
    }
    
    /**
     * Test endpoint to send multiple WebSocket messages
     */
    @GetMapping("/send-multiple")
    public String sendMultipleTestMessages(@RequestParam(defaultValue = "5") int count) {
        String[] symbols = {"BTC", "ETH", "ADA", "XRP", "LTC", "BCH", "DOT", "LINK", "SOL", "DOGE"};
        
        for (int i = 0; i < count; i++) {
            String symbol = symbols[random.nextInt(symbols.length)];
            BigDecimal price = BigDecimal.valueOf(1000 + random.nextDouble() * 9000);
            
            CryptoPriceUpdate update = new CryptoPriceUpdate(
                symbol,
                price,
                Instant.now().toEpochMilli()
            );
            
            logger.info("Sending test message {} of {}: {}", i + 1, count, update);
            messagingTemplate.convertAndSend("/topic/prices", update);
            
            try {
                // Small delay between messages
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Interrupted after " + (i + 1) + " messages";
            }
        }
        
        return String.format("Sent %d test messages to /topic/prices", count);
    }
}
