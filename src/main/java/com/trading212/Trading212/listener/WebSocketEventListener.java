package com.trading212.Trading212.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Listener for WebSocket events to track connections, disconnections, and subscriptions.
 */
@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        logger.info("WebSocket connection established - Session ID: {}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        logger.info("WebSocket connection closed - Session ID: {}", sessionId);
    }

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        String sessionId = event.getUser() != null ? event.getUser().getName() : "unknown";
        String destination = (String) event.getMessage().getHeaders().get("simpDestination");
        logger.info("New subscription - Session: {}, Destination: {}", sessionId, destination);
    }

    @EventListener
    public void handleSessionUnsubscribeEvent(SessionUnsubscribeEvent event) {
        String sessionId = event.getUser() != null ? event.getUser().getName() : "unknown";
        logger.info("Subscription ended - Session: {}", sessionId);
    }
}
