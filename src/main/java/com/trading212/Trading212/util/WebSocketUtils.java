package com.trading212.Trading212.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Map;

/**
 * Utility class for WebSocket operations and debugging.
 */
public class WebSocketUtils {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketUtils.class);

    // Prevent instantiation
    private WebSocketUtils() {}

    /**
     * Logs details about a WebSocket message.
     *
     * @param message the message to log
     * @param isInbound true if the message is incoming, false if outgoing
     */
    public static void logMessage(Message<?> message, boolean isInbound) {
        if (!logger.isDebugEnabled()) {
            return;
        }

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return;
        }

        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        String subscriptionId = accessor.getSubscriptionId();
        String messageId = accessor.getMessageId();
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append(isInbound ? "INBOUND" : "OUTBOUND").append(" ");
        logMessage.append("Command: ").append(command).append(", ");
        logMessage.append("Session: ").append(sessionId).append(", ");
        
        if (destination != null) {
            logMessage.append("Destination: ").append(destination).append(", ");
        }
        
        if (subscriptionId != null) {
            logMessage.append("Subscription: ").append(subscriptionId).append(", ");
        }
        
        if (messageId != null) {
            logMessage.append("Message ID: ").append(messageId).append(", ");
        }
        
        // Log headers for CONNECT and SUBSCRIBE commands
        if (command == StompCommand.CONNECT || command == StompCommand.SUBSCRIBE) {
            logMessage.append("Headers: ").append(accessor.toMap());
        } else {
            // For other commands, just log the payload type
            if (message.getPayload() != null) {
                logMessage.append("Payload Type: ").append(message.getPayload().getClass().getSimpleName());
            }
        }

        logger.debug(logMessage.toString());
    }

    /**
     * Extracts the user principal name from the message headers.
     *
     * @param accessor the StompHeaderAccessor for the message
     * @return the user principal name, or "anonymous" if not available
     */
    public static String getUserPrincipalName(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null && accessor.getUser().getName() != null) {
            return accessor.getUser().getName();
        }
        return "anonymous";
    }

    /**
     * Gets a string representation of the message for logging purposes.
     *
     * @param message the message to convert to string
     * @return a string representation of the message
     */
    public static String messageToString(Message<?> message) {
        if (message == null) {
            return "null";
        }

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Message [");
        sb.append("command=").append(accessor.getCommand());
        sb.append(", sessionId=").append(accessor.getSessionId());
        sb.append(", destination=").append(accessor.getDestination());
        sb.append(", subscriptionId=").append(accessor.getSubscriptionId());
        sb.append(", messageId=").append(accessor.getMessageId());
        
        Object payload = message.getPayload();
        if (payload != null) {
            sb.append(", payloadType=").append(payload.getClass().getName());
            if (payload instanceof byte[]) {
                sb.append(", payloadSize=").append(((byte[]) payload).length).append(" bytes");
            } else if (payload instanceof String) {
                String strPayload = (String) payload;
                sb.append(", payloadLength=").append(strPayload.length());
                if (strPayload.length() < 100) {
                    sb.append(", payload=\"").append(strPayload).append("\"");
                }
            }
        }
        
        sb.append("]");
        return sb.toString();
    }
}
