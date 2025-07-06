package com.trading212.Trading212.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller for testing WebSocket functionality.
 * Provides various endpoints to test different WebSocket features.
 */
@Controller
public class WebSocketTestController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTestController.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicInteger testCounter = new AtomicInteger(0);
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    public WebSocketTestController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Simple echo test endpoint that returns the received message
     */
    @MessageMapping("/test/echo")
    @SendTo("/topic/test/echo")
    public Map<String, Object> echoTest(@Payload Map<String, Object> message, 
                                      Principal principal,
                                      SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        logger.info("Echo test request from session: {}, user: {}", sessionId, 
                   principal != null ? principal.getName() : "anonymous");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("receivedMessage", message);
        response.put("sessionId", sessionId);
        response.put("serverTime", System.currentTimeMillis());
        
        return response;
    }

    /**
     * Test endpoint that sends a response to a specific user
     */
    @MessageMapping("/test/private")
    @SendToUser("/queue/private")
    public Map<String, Object> privateTest(@Payload Map<String, Object> message, Principal principal) {
        logger.info("Private test request from user: {}", principal != null ? principal.getName() : "anonymous");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "private_success");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("message", "This is a private message for you");
        response.put("originalRequest", message);
        
        return response;
    }

    /**
     * Sends a test message every 10 seconds to all subscribers of /topic/updates
     */
    @Scheduled(fixedRate = 10000)
    public void sendPeriodicUpdate() {
        int counter = testCounter.incrementAndGet();
        Map<String, Object> update = new HashMap<>();
        update.put("counter", counter);
        update.put("message", "Periodic update #" + counter);
        update.put("timestamp", LocalDateTime.now().toString());
        update.put("activeSessions", activeSessions.size());
        
        logger.debug("Sending periodic update: {}", update);
        
        // Send to a topic that the frontend might be listening to
        messagingTemplate.convertAndSend("/topic/prices", createTestPriceUpdate("BTC/USD"));
        messagingTemplate.convertAndSend("/topic/prices", createTestPriceUpdate("ETH/USD"));
        
        // Also send to a test topic
        messagingTemplate.convertAndSend("/topic/updates", update);
    }
    
    /**
     * Tracks active WebSocket sessions
     */
    @org.springframework.context.event.EventListener
    public void handleWebSocketConnectListener(org.springframework.web.socket.messaging.SessionConnectedEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        String username = (event.getUser() != null) ? event.getUser().getName() : "anonymous";
        activeSessions.put(sessionId, username);
        logger.info("New WebSocket connection: session={}, user={}, total={}", 
                   sessionId, username, activeSessions.size());
    }
    
    /**
     * Cleans up disconnected WebSocket sessions
     */
    @org.springframework.context.event.EventListener
    public void handleWebSocketDisconnectListener(org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String username = activeSessions.remove(sessionId);
        logger.info("WebSocket disconnected: session={}, user={}, remaining={}", 
                   sessionId, username, activeSessions.size());
    }
    
    /**
     * Creates a test price update for the given symbol
     */
    /**
     * Creates a test price update for the given symbol
     */
    public Map<String, Object> createTestPriceUpdate(String symbol) {
        messageCounter.incrementAndGet();
        Map<String, Object> update = new HashMap<>();
        update.put("symbol", symbol);
        update.put("newPrice", 50000 + (Math.random() * 1000));
        update.put("timestamp", System.currentTimeMillis());
        update.put("priceChange", (Math.random() * 100) - 50);
        update.put("priceChangePercent", (Math.random() * 5) - 2.5);
        update.put("id", UUID.randomUUID().toString());
        return update;
    }
    
    /**
     * Broadcasts a price update to all subscribers
     */
    public void broadcastPriceUpdate(Map<String, Object> priceUpdate) {
        messagingTemplate.convertAndSend("/topic/prices", priceUpdate);
        logger.debug("Broadcasted price update: {}", priceUpdate);
    }
    
    /**
     * Sends a test message to a specific user
     */
    public void sendPrivateMessage(String username, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("messageId", UUID.randomUUID().toString());
        
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", payload);
        logger.info("Sent private message to user: {}, message: {}", username, message);
    }
    
    /**
     * Gets the number of active WebSocket sessions
     */
    public int getActiveSessionsCount() {
        return activeSessions.size();
    }
    
    /**
     * Gets the total number of test messages sent
     */
    public int getTestMessageCount() {
        return messageCounter.get();
    }
    
    /**
     * Broadcasts a message to all connected clients
     */
    public void broadcastMessage(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "broadcast");
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("messageId", UUID.randomUUID().toString());
        
        messagingTemplate.convertAndSend("/topic/broadcast", payload);
        logger.info("Broadcasted message: {}", message);
    }
}
