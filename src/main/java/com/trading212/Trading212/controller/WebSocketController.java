package com.trading212.Trading212.controller;

import com.trading212.Trading212.model.CryptoPriceUpdate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    @MessageMapping("/update-prices")
    @SendTo("/topic/prices")
    public CryptoPriceUpdate updatePrices(CryptoPriceUpdate update) {
        return update;
    }
}
