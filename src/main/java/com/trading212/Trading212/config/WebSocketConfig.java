package com.trading212.Trading212.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import com.trading212.Trading212.util.WebSocketUtils;

/**
 * Configuration for WebSocket and STOMP messaging.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    // WebSocket endpoint clients will use to connect to the server
    public static final String WS_ENDPOINT = "/ws";
    // Prefix for topics where the server will send messages to clients
    public static final String TOPIC_PREFIX = "/topic";
    // Prefix for application endpoints where clients can send messages
    public static final String APP_PREFIX = "/app";
    
    // Heartbeat configuration (in milliseconds)
    private static final long[] HEARTBEAT = new long[] {10000, 10000}; // 10 seconds
    
    // WebSocket logging interceptor
    @Bean
    public WebSocketLoggingInterceptor webSocketLoggingInterceptor() {
        return new WebSocketLoggingInterceptor();
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Create a custom task scheduler for heartbeats
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setPoolSize(1);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();

        // Configure the message broker with heartbeat settings
        registry.enableSimpleBroker(TOPIC_PREFIX)
                .setTaskScheduler(scheduler)
                .setHeartbeatValue(HEARTBEAT);
        
        // Designate the "/app" prefix for messages that are bound for @MessageMapping methods
        registry.setApplicationDestinationPrefixes(APP_PREFIX);
        
        // Enable user destinations (for user-specific messages)
        registry.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the WebSocket endpoint
        registry.addEndpoint(WS_ENDPOINT)
                .setAllowedOriginPatterns("*") // Allow all origins (adjust in production)
                .addInterceptors(
                    webSocketLoggingInterceptor(),
                    new HttpSessionHandshakeInterceptor()
                )
                .withSockJS()
                .setSessionCookieNeeded(true) // Enable session cookie for sticky sessions if using load balancing
                .setHeartbeatTime(25000) // 25 seconds (must be less than the server's timeout)
                .setWebSocketEnabled(true) // Enable WebSocket transport
                .setStreamBytesLimit(512 * 1024) // 512KB
                .setHttpMessageCacheSize(1000) // Number of messages to buffer
                .setDisconnectDelay(30 * 1000); // 30 seconds disconnect delay
    }


    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Configure message size limits
        registration.setMessageSizeLimit(128 * 1024); // 128KB
        registration.setSendBufferSizeLimit(512 * 1024); // 512KB
        registration.setSendTimeLimit(20000); // 20 seconds
        
        // Configure timeouts and other transport settings
        registration.setTimeToFirstMessage(30000); // 30 seconds to first message
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Configure the inbound channel (messages from client to server)
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Log the incoming message using our utility class
                WebSocketUtils.logMessage(message, true);
                return message;
            }
            
            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                if (ex != null) {
                    logger.error("Error processing inbound WebSocket message: {}", ex.getMessage(), ex);
                }
            }
        });
    }
    
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Configure the outbound channel (messages from server to client)
        registration.taskExecutor()
            .corePoolSize(4)
            .maxPoolSize(10)
            .keepAliveSeconds(30);
            
        // Add interceptors for outbound messages
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Log the outbound message using our utility class
                WebSocketUtils.logMessage(message, false);
                return message;
            }
            
            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                if (ex != null) {
                    logger.error("Error sending outbound WebSocket message: {}", ex.getMessage(), ex);
                }
            }
        });
    }
}