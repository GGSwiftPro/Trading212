package com.trading212.Trading212.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PingController {

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public Map<String, Object> handlePing(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "pong");
        response.put("timestamp", Instant.now().toEpochMilli());
        response.put("clientTimestamp", payload != null ? payload.get("timestamp") : null);
        response.put("serverTime", Instant.now().toString());
        return response;
    }
}
