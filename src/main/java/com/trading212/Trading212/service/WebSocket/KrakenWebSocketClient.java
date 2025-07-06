package com.trading212.Trading212.service.WebSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trading212.Trading212.repository.CryptoRepository;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class KrakenWebSocketClient extends WebSocketListener {
    private static final String KRAKEN_WEBSOCKET_URL = "wss://ws.kraken.com/";
    private static final List<String> KRAKEN_PAIRS = Arrays.asList(
            "XBT/USD", "ETH/USD", "ADA/USD", "XRP/USD", "LTC/USD",
            "BCH/USD", "DOT/USD", "LINK/USD", "SOL/USD", "UNI/USD",
            "DOGE/USD", "TRX/USD", "ETC/USD", "XLM/USD", "EOS/USD",
            "XTZ/USD", "ATOM/USD", "FIL/USD", "VET/USD", "NEO/USD"
    );

    private final CryptoRepository cryptoRepository;
    private final ObjectMapper objectMapper;
    private WebSocket webSocket;
    private OkHttpClient client;

    @Autowired
    public KrakenWebSocketClient(CryptoRepository cryptoRepository) {
        this.cryptoRepository = cryptoRepository;
        this.objectMapper = new ObjectMapper();
    }


    @PostConstruct
    public void startWebSocket() {
        System.out.println("Attempting to connect to Kraken WebSocket API...");
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(KRAKEN_WEBSOCKET_URL)
                .build();
                
        webSocket = client.newWebSocket(request, this);
        client.dispatcher().executorService().shutdown(); // Clean up the executor service
    }

    /**
     * Closes the WebSocket connection gracefully when the application shuts down.
     */
    @PreDestroy
    public void stopWebSocket() {
        if (webSocket != null) {
            System.out.println("Closing Kraken WebSocket connection...");
            webSocket.close(1000, "Application shutting down");
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown(); // Shut down OkHttp's thread pool
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        System.out.println("Connected to Kraken WebSocket API");
        
        // Subscribe to ticker updates for all pairs
        String subscribeMessage = createSubscribeMessage(KRAKEN_PAIRS);
        if (subscribeMessage != null) {
            webSocket.send(subscribeMessage);
            System.out.println("Subscribed to ticker updates");
        }
    }

    private String createSubscribeMessage(List<String> pairs) {
        try {
            // Filter out unsupported pairs
            List<String> supportedPairs = pairs.stream()
                    .filter(pair -> !pair.equals("VET/USD") && !pair.equals("NEO/USD"))
                    .collect(Collectors.toList());
            
            if (supportedPairs.isEmpty()) {
                System.out.println("No supported pairs to subscribe to");
                return null;
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
            return message;
        } catch (Exception e) {
            System.err.println("Error creating subscribe message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            System.out.println("Raw WebSocket message: " + text); // Log raw message
            JsonNode rootNode = objectMapper.readTree(text);

            // Handle ticker updates
            if (rootNode.isArray() && rootNode.size() >= 4) {
                try {
                    // The message is an array where:
                    // [0] = channelID
                    // [1] = ticker data object
                    // [2] = channel name (e.g., "ticker")
                    // [3] = pair name (e.g., "XBT/USD")
                    if (rootNode.size() >= 4) {
                        String pairName = rootNode.get(3).asText();
                        JsonNode tickerData = rootNode.get(1);
                        
                        if (tickerData != null && tickerData.isObject()) {
                            // The 'c' field is an array where the first element is the last trade price
                            JsonNode cNode = tickerData.path("c");
                            if (!cNode.isMissingNode() && cNode.isArray() && cNode.size() > 0) {
                                String lastTradePrice = cNode.get(0).asText();
                                try {
                                    BigDecimal newPrice = new BigDecimal(lastTradePrice);
                                    
                                    // Log the update with more details
                                    System.out.println("=== PRICE UPDATE ===");
                                    System.out.println("Pair: " + pairName);
                                    System.out.println("New Price: " + newPrice);
                                    
                                    // Update the price in the database
                                    try {
                                        cryptoRepository.updatePrice(pairName, newPrice);
                                        System.out.println("Successfully updated price in database");
                                    } catch (Exception e) {
                                        System.err.println("Error updating price in database: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                } catch (NumberFormatException e) {
                                    System.err.println("Failed to parse price: " + lastTradePrice);
                                }
                            } else {
                                System.out.println("No price data available in ticker update");
                                System.out.println("Ticker data: " + tickerData.toString());
                            }
                        } else {
                            System.out.println("Invalid ticker data format");
                        }
                    } else {
                        System.out.println("Unexpected message format: " + rootNode.toString());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing ticker data: " + e.getMessage());
                    e.printStackTrace();
                }
            } 
            // Handle system status messages
            else if (rootNode.isObject()) {
                if (rootNode.has("event")) {
                    String event = rootNode.get("event").asText();
                    if (event.equals("systemStatus")) {
                        System.out.println("=== KRAKEN SYSTEM STATUS ===");
                        System.out.println("Status: " + rootNode.get("status").asText());
                        System.out.println("Version: " + rootNode.get("version").asText());
                    } else if (event.equals("subscriptionStatus") && rootNode.has("status")) {
                        System.out.println("=== SUBSCRIPTION STATUS ===");
                        System.out.println("Status: " + rootNode.get("status").asText());
                        System.out.println("Pair: " + rootNode.get("pair").asText());
                        if (rootNode.has("errorMessage")) {
                            System.out.println("Error: " + rootNode.get("errorMessage").asText());
                        }
                    } else {
                        System.out.println("Unhandled event type: " + event);
                    }
                } else {
                    System.out.println("Unknown message type: " + rootNode.toString());
                }
            } else {
                System.out.println("Received message with unexpected format: " + text);
            }
        } catch (Exception e) {
            System.err.println("Error processing WebSocket message: " + e.getMessage());
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        System.out.println("Closing WebSocket: " + code + " / " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        System.err.println("WebSocket connection failure: " + t.getMessage());
        t.printStackTrace();
        if (response != null) {
            System.err.println("Response: " + response.code() + " " + response.message());
        }
        // Implement re-connection logic here if needed for continuous operation
        // For a coding task, a simple restart might be sufficient, or just logging.
    }


}
