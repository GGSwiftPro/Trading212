package com.trading212.Trading212.controller;

import com.trading212.Trading212.model.CryptoPriceUpdate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final SimpMessagingTemplate messagingTemplate;

    public TestController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/send-test-update")
    public String sendTestUpdate() {
        CryptoPriceUpdate update = new CryptoPriceUpdate(
            "BTC",
            new BigDecimal("50000.00"),
            Instant.now().toEpochMilli()
        );
        
        messagingTemplate.convertAndSend("/topic/prices", update);
        return "Test update sent: " + update;
    }
}
