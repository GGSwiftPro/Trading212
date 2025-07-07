package com.trading212.Trading212.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading212.Trading212.model.CryptoPriceUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public WebSocketController(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Handles incoming price updates and broadcasts them to all subscribers
     */
    @MessageMapping("/update-prices")
    @SendTo("/topic/prices")
    public CryptoPriceUpdate handlePriceUpdate(@Payload CryptoPriceUpdate update, 
                                             SimpMessageHeaderAccessor headerAccessor) {
        if (update == null || update.getSymbol() == null) {
            logger.warn("Received invalid price update: {}", update);
            return null;
        }
        
        logger.debug("Received price update: {}", update);
        
        // The @SendTo annotation will automatically send the return value to /topic/prices
        return update;
    }
    
    /**
     * Broadcasts a price update to all subscribers
     */
    public void broadcastPriceUpdate(CryptoPriceUpdate update) {
        if (update == null || update.getSymbol() == null) {
            logger.warn("Attempted to broadcast invalid price update: {}", update);
            return;
        }
        
        try {
            String jsonUpdate = objectMapper.writeValueAsString(update);
            logger.debug("Broadcasting price update: {}", jsonUpdate);
            messagingTemplate.convertAndSend("/topic/prices", jsonUpdate);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing price update: {}", e.getMessage(), e);
        }
    }
}
