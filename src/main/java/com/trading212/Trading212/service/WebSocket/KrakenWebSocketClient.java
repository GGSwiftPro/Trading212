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
import org.slf4j.Logger; // Import Logger
import org.slf4j.LoggerFactory; // Import LoggerFactory
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate; // Import SimpMessagingTemplate
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional; // Import Optional
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class KrakenWebSocketClient extends WebSocketListener {

    private static final Logger logger = LoggerFactory.getLogger(KrakenWebSocketClient.class); // Initialize Logger

    private static final String KRAKEN_WEBSOCKET_URL = "wss://ws.kraken.com/";
    private static final List<String> KRAKEN_PAIRS = Arrays.asList(
            "XBT/USD", "ETH/USD", "ADA/USD", "XRP/USD", "LTC/USD",
            "BCH/USD", "DOT/USD", "LINK/USD", "SOL/USD", "UNI/USD",
            "DOGE/USD", "TRX/USD", "ETC/USD", "XLM/USD", "EOS/USD",
            "XTZ/USD", "ATOM/USD", "FIL/USD", "VET/USD", "NEO/USD"
    );

    private final CryptoRepository cryptoRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate; // Inject SimpMessagingTemplate
    private WebSocket webSocket;
    private OkHttpClient client;

    @Autowired
    public KrakenWebSocketClient(CryptoRepository cryptoRepository, SimpMessagingTemplate messagingTemplate) { // Inject messagingTemplate
        this.cryptoRepository = cryptoRepository;
        this.objectMapper = new ObjectMapper();
        this.messagingTemplate = messagingTemplate; // Assign it
    }


    @PostConstruct
    public void startWebSocket() {
        logger.info("Attempting to connect to Kraken WebSocket API..."); // Use logger
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(KRAKEN_WEBSOCKET_URL)
                .build();

        webSocket = client.newWebSocket(request, this);
        // client.dispatcher().executorService().shutdown(); // Do NOT shutdown here, it's a persistent client
    }

    /**
     * Closes the WebSocket connection gracefully when the application shuts down.
     */
    @PreDestroy
    public void stopWebSocket() {
        if (webSocket != null) {
            logger.info("Closing Kraken WebSocket connection..."); // Use logger
            webSocket.close(1000, "Application shutting down");
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown(); // Shut down OkHttp's thread pool
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        logger.info("Connected to Kraken WebSocket API"); // Use logger

        // Subscribe to ticker updates for all pairs
        String subscribeMessage = createSubscribeMessage(KRAKEN_PAIRS);
        if (subscribeMessage != null) {
            webSocket.send(subscribeMessage);
            logger.info("Subscribed to ticker updates"); // Use logger
        }
    }

    private String createSubscribeMessage(List<String> pairs) {
        try {
            // Filter out unsupported pairs
            List<String> supportedPairs = pairs.stream()
                    .filter(pair -> !pair.equals("VET/USD") && !pair.equals("NEO/USD")) // These pairs might not be directly supported by Kraken's ticker stream or have different symbols.
                    .collect(Collectors.toList());

            if (supportedPairs.isEmpty()) {
                logger.warn("No supported pairs to subscribe to"); // Use logger
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
            logger.info("Sending subscribe message: {}", message); // Use logger
            return message;
        } catch (Exception e) {
            logger.error("Error creating subscribe message: {}", e.getMessage(), e); // Use logger
            return null;
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            // logger.debug("Raw WebSocket message: {}", text); // Use debug for very verbose logs
            JsonNode rootNode = objectMapper.readTree(text);

            // Handle ticker updates
            if (rootNode.isArray() && rootNode.size() >= 4) {
                try {
                    String channelName = rootNode.get(2).asText(); // e.g., "ticker"
                    String pairName = rootNode.get(3).asText(); // e.g., "XBT/USD"
                    JsonNode tickerData = rootNode.get(1);

                    if ("ticker".equals(channelName) && tickerData != null && tickerData.isObject()) {
                        JsonNode cNode = tickerData.path("c"); // Last trade closed price
                        if (!cNode.isMissingNode() && cNode.isArray() && cNode.size() > 0) {
                            String lastTradePriceStr = cNode.get(0).asText();
                            try {
                                BigDecimal newPrice = new BigDecimal(lastTradePriceStr);

                                // Optional: Get 24h percent change and volume if available and needed for frontend
                                // Kraken ticker data structure:
                                // "v": [today, last 24 hours]
                                // "p": [today, last 24 hours] (volume weighted average price)
                                // "c": [last trade closed price, last trade volume]
                                JsonNode vNode = tickerData.path("v"); // Volume [today, last 24 hours]
                                JsonNode pNode = tickerData.path("p"); // VWAP [today, last 24 hours]

                                BigDecimal volume24h = BigDecimal.ZERO;
                                // Kraken's 'p' field (volume weighted average price) can be used to derive percent change
                                // If your backend already calculates it, use that. Otherwise, calculate or skip.
                                // For simplicity, we'll just forward price and symbol for now.
                                // You might need to fetch the crypto from DB to get old price for % change calculation.

                                // Simplified example: Just send symbol and newPrice for frontend update
                                // Frontend expects 'symbol', 'currentPrice', 'percentChange24h', 'volume24h'
                                // We'll map Kraken's 'newPrice' to 'currentPrice' and add dummy/placeholder for others.

                                // Map Kraken's symbols to your simplified symbols (e.g., XBT/USD -> BTC)
                                String frontendSymbol = mapKrakenSymbolToFrontend(pairName);
                                if (frontendSymbol == null) {
                                    logger.warn("Could not map Kraken pair {} to frontend symbol. Skipping update.", pairName);
                                    return;
                                }

                                // Construct the message for the frontend
                                // This matches the format updateCryptoCard expects
                                ObjectNode priceUpdateMessage = objectMapper.createObjectNode();
                                priceUpdateMessage.put("symbol", frontendSymbol);
                                priceUpdateMessage.put("newPrice", newPrice); // Frontend expects newPrice
                                // You can add actual percentChange24h and volume24h if you calculate them
                                // For now, sending placeholders to prevent frontend errors
                                priceUpdateMessage.put("percentChange24h", 0.0); // Placeholder
                                priceUpdateMessage.put("volume24h", 0.0); // Placeholder
                                priceUpdateMessage.put("timestamp", System.currentTimeMillis());

                                logger.info("Sending price update to frontend: {}", priceUpdateMessage.toString()); // Use logger

                                // Send to all connected WebSocket clients on /topic/prices
                                messagingTemplate.convertAndSend("/topic/prices", priceUpdateMessage);

                                // Update the price in the database (already present)
                                try {
                                    cryptoRepository.updatePrice(pairName, newPrice);
                                    logger.debug("Successfully updated price in database for {}", pairName);
                                } catch (Exception e) {
                                    logger.error("Error updating price in database for {}: {}", pairName, e.getMessage(), e);
                                }

                            } catch (NumberFormatException e) {
                                logger.error("Failed to parse price: {} for pair {}", lastTradePriceStr, pairName, e);
                            }
                        } else {
                            logger.debug("No valid 'c' node (last trade price) in ticker update for pair {}", pairName);
                        }
                    } else {
                        logger.debug("Received non-ticker array message or invalid ticker format: {}", rootNode.toString());
                    }
                } catch (Exception e) {
                    logger.error("Error processing array WebSocket message: {}", e.getMessage(), e);
                }
            }
            // Handle system status messages and other non-array messages
            else if (rootNode.isObject() && rootNode.has("event")) {
                String event = rootNode.get("event").asText();
                if ("systemStatus".equals(event)) {
                    logger.info("=== KRAKEN SYSTEM STATUS ===");
                    logger.info("Status: {}", rootNode.get("status").asText());
                    logger.info("Version: {}", rootNode.get("version").asText());
                } else if ("subscriptionStatus".equals(event) && rootNode.has("status")) {
                    logger.info("=== SUBSCRIPTION STATUS ===");
                    logger.info("Status: {}", rootNode.get("status").asText());
                    logger.info("Pair: {}", rootNode.path("pair").asText("N/A")); // Use path for robustness
                    if (rootNode.has("errorMessage")) {
                        logger.warn("Subscription Error: {}", rootNode.get("errorMessage").asText());
                    }
                } else if ("heartbeat".equals(event)) {
                    // logger.debug("Received Kraken heartbeat"); // Too verbose, use debug if needed
                }
                else {
                    logger.info("Unhandled Kraken event type: {} - Message: {}", event, rootNode.toString());
                }
            } else {
                logger.debug("Received WebSocket message with unexpected top-level format: {}", text);
            }
        } catch (Exception e) {
            logger.error("Error processing raw WebSocket message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        logger.info("Closing WebSocket: {} / {}", code, reason); // Use logger
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        logger.error("WebSocket connection failure: {}", t.getMessage(), t); // Use logger
        if (response != null) {
            logger.error("Failure Response: {} {}", response.code(), response.message()); // Use logger
        }
        // Implement re-connection logic here if needed
    }

    private String mapKrakenSymbolToFrontend(String krakenPair) {
        switch (krakenPair) {
            case "XBT/USD": return "BTC"; // XBT is Bitcoin on Kraken
            case "ETH/USD": return "ETH";
            case "ADA/USD": return "ADA";
            case "XRP/USD": return "XRP";
            case "LTC/USD": return "LTC";
            case "BCH/USD": return "BCH";
            case "DOT/USD": return "DOT";
            case "LINK/USD": return "LINK";
            case "SOL/USD": return "SOL";
            case "UNI/USD": return "UNI";
            case "XDG/USD": return "DOGE"; // XDG is Dogecoin on Kraken
            case "TRX/USD": return "TRX";
            case "ETC/USD": return "ETC";
            case "XLM/USD": return "XLM";
            case "EOS/USD": return "EOS";
            case "XTZ/USD": return "XTZ";
            case "ATOM/USD": return "ATOM";
            case "FIL/USD": return "FIL";
            // Add mappings for VET/USD and NEO/USD if they exist and you get them elsewhere
            // They were filtered out of KRAKEN_PAIRS for subscription, so won't come from ticker.
            default: return null; // Indicate unmapped symbol
        }
    }
}