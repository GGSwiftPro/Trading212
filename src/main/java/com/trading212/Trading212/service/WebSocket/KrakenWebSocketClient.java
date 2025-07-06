package com.trading212.Trading212.service.WebSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trading212.Trading212.model.CryptoPriceUpdate;
import com.trading212.Trading212.repository.CryptoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class KrakenWebSocketClient implements WebSocket.Listener {
    private static final String KRAKEN_WEBSOCKET_URL = "wss://ws.kraken.com/";
    private static final List<String> KRAKEN_PAIRS = Arrays.asList(
            "XBT/USD", "ETH/USD", "ADA/USD", "XRP/USD", "LTC/USD",
            "BCH/USD", "DOT/USD", "LINK/USD", "SOL/USD", "UNI/USD",
            "DOGE/USD", "TRX/USD", "ETC/USD", "XLM/USD", "EOS/USD",
            "XTZ/USD", "ATOM/USD", "FIL/USD"
    );

    private final CryptoRepository cryptoRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private WebSocket webSocket;
    private final HttpClient httpClient;
    private CompletableFuture<WebSocket> webSocketFuture;

    @Autowired
    public KrakenWebSocketClient(CryptoRepository cryptoRepository, SimpMessagingTemplate messagingTemplate) {
        this.cryptoRepository = cryptoRepository;
        this.objectMapper = new ObjectMapper();
        this.messagingTemplate = messagingTemplate;
        this.httpClient = HttpClient.newHttpClient();
    }

    @PostConstruct
    public void startWebSocket() {
        System.out.println("Attempting to connect to Kraken WebSocket API...");
        connectWebSocket();
    }

    private void connectWebSocket() {
        try {
            webSocketFuture = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(KRAKEN_WEBSOCKET_URL), this)
                    .thenApply(ws -> {
                        this.webSocket = ws;
                        return ws;
                    });
            
            // Wait for connection to be established
            webSocketFuture.get(10, TimeUnit.SECONDS);
            System.out.println("WebSocket connection established");
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("Failed to establish WebSocket connection: " + e.getMessage());
            e.printStackTrace();
            scheduleReconnect();
        }
    }
    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("Connected to Kraken WebSocket API");
        webSocket.sendText(createSubscribeMessage(KRAKEN_PAIRS).orElse(""), true);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            System.out.println("Received message: " + data);
            JsonNode rootNode = objectMapper.readTree(data.toString());
            processWebSocketMessage(rootNode);
        } catch (IOException e) {
            System.err.println("Error processing WebSocket message: " + e.getMessage());
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.printf("WebSocket connection closed: %d - %s%n", statusCode, reason);
        scheduleReconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("WebSocket error: " + error.getMessage());
        error.printStackTrace();
        scheduleReconnect();
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        // Handle binary messages if needed
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.sendPong(message);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        // Handle pong message
        return CompletableFuture.completedFuture(null);
    }

    private void processWebSocketMessage(JsonNode message) {
        try {
            // Check if this is a ticker update message
            if (message.isArray() && message.size() >= 4) {
                String pairName = message.get(3).asText();
                JsonNode tickerData = message.get(1);
                
                if (tickerData != null && tickerData.isObject()) {
                    JsonNode cNode = tickerData.path("c");
                    if (!cNode.isMissingNode() && cNode.isArray() && cNode.size() > 0) {
                        String lastTradePrice = cNode.get(0).asText();
                        try {
                            BigDecimal newPrice = new BigDecimal(lastTradePrice);
                            // Update the price in the database
                            cryptoRepository.updatePrice(pairName, newPrice);
                            
                            // Broadcast the price update to WebSocket subscribers
                            CryptoPriceUpdate update = new CryptoPriceUpdate();
                            update.setSymbol(pairName);
                            update.setNewPrice(newPrice);
                            update.setTimestamp(System.currentTimeMillis());
                            
                            // Send the update to the WebSocket topic
                            messagingTemplate.convertAndSend("/topic/prices", update);
                            
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid price format for " + pairName + ": " + lastTradePrice);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing WebSocket message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void scheduleReconnect() {
        System.out.println("Scheduling reconnect in 5 seconds...");
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                connectWebSocket();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @PreDestroy
    public void stopWebSocket() {
        if (webSocket != null) {
            System.out.println("Closing WebSocket connection...");
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Application shutting down");
        }
        if (webSocketFuture != null) {
            webSocketFuture.cancel(true);
        }
    }

    private Optional<String> createSubscribeMessage(List<String> pairs) {
        try {
            // Filter out unsupported pairs
            List<String> supportedPairs = pairs.stream()
                    .filter(pair -> !pair.equals("VET/USD") && !pair.equals("NEO/USD"))
                    .collect(Collectors.toList());
            
            if (supportedPairs.isEmpty()) {
                System.out.println("No supported pairs to subscribe to");
                return Optional.empty();
            }
            
            ObjectNode subscribeRequest = objectMapper.createObjectNode();
            subscribeRequest.put("event", "subscribe");
            
            // Create subscription object
            ObjectNode subscription = objectMapper.createObjectNode();
            subscription.put("name", "ticker");
            
            // Add subscription to request
            subscribeRequest.set("subscription", subscription);
            
            // Add pairs to request
            ArrayNode pairArray = objectMapper.createArrayNode();
            supportedPairs.forEach(pairArray::add);
            subscribeRequest.set("pair", pairArray);
            
            String message = objectMapper.writeValueAsString(subscribeRequest);
            System.out.println("Sending subscribe message: " + message);
            return Optional.of(message);
        } catch (Exception e) {
            System.err.println("Error creating subscribe message: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
