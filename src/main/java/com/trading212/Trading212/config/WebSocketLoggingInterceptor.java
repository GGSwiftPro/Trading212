package com.trading212.Trading212.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor for logging WebSocket handshake requests and responses
 */
public class WebSocketLoggingInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketLoggingInterceptor.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            // Log connection attempt
            logger.info("WebSocket connection attempt from: {} {}", 
                       servletRequest.getRemoteAddress(),
                       servletRequest.getHeaders().get("User-Agent"));
            
            // Log headers for debugging
            logger.debug("WebSocket Headers: {}", servletRequest.getHeaders());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                              WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            logger.error("WebSocket handshake failed: {}", exception.getMessage(), exception);
        } else if (request instanceof ServletServerHttpRequest) {
            logger.info("WebSocket connection established: {}", 
                       ((ServletServerHttpRequest) request).getRemoteAddress());
        }
    }
}
