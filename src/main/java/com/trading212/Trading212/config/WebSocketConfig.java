package com.trading212.Trading212.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedMethods("*")
                        .allowedHeaders("*");
            }
        };
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        System.out.println("Configuring message broker");
        // Enable a simple in-memory message broker to carry the messages back to the client
        config.enableSimpleBroker("/topic");
        
        // Designate the /app prefix for messages that are bound to @MessageMapping-annotated methods
        config.setApplicationDestinationPrefixes("/app");
        
        // Set the user destination prefix
        config.setUserDestinationPrefix("/user");
        
        // Configure message conversion
        config.configureBrokerChannel().interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                System.out.println("Sending message: " + message);
                return message;
            }
        });
        
        System.out.println("Message broker configured with application destination prefix: /app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        System.out.println("Registering STOMP endpoints");
        
        // Register the /ws endpoint, enabling SockJS fallback options
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Allow all origins for development
                .withSockJS()
                .setStreamBytesLimit(512 * 1024) // 512KB
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000) // 30 seconds
                .setSessionCookieNeeded(false)
                .setWebSocketEnabled(true)
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1.5.0/dist/sockjs.min.js");
                
        System.out.println("STOMP endpoint registered at /ws with SockJS support");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        System.out.println("Configuring WebSocket transport");
        registration
            .setSendTimeLimit(15 * 1000) // 15 seconds
            .setSendBufferSizeLimit(512 * 1024) // 512KB
            .setMessageSizeLimit(128 * 1024) // 128KB
            .setTimeToFirstMessage(30000); // 30 seconds
            
        System.out.println("WebSocket transport configured with timeouts and limits");
    }
    
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        System.out.println("Configuring message converters");
        
        // Configure content type resolver
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        
        // Configure JSON message converter
        MappingJackson2MessageConverter jsonConverter = new MappingJackson2MessageConverter();
        jsonConverter.setObjectMapper(objectMapper());
        jsonConverter.setContentTypeResolver(resolver);
        
        // Configure string message converter
        StringMessageConverter stringConverter = new StringMessageConverter();
        stringConverter.setSerializedPayloadClass(String.class);
        
        // Add converters in order of preference
        messageConverters.add(jsonConverter);
        messageConverters.add(stringConverter);
        messageConverters.add(new ByteArrayMessageConverter());
        
        System.out.println("Message converters configured with JSON and String support");
        
        // Return false to indicate we don't want the default converters added
        return false;
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    @Bean
    public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}