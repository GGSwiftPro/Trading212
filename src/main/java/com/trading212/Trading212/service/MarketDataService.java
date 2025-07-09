package com.trading212.Trading212.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketDataService {
    
    @Value("${coingecko.api.url:https://api.coingecko.com/api/v3}")
    private String apiUrl;
    
    private final WebClient webClient;
    private final Map<String, Integer> rankCache = new ConcurrentHashMap<>();
    
    public MarketDataService(WebClient webClient) {
        this.webClient = webClient;
    }
    
    @SuppressWarnings("unchecked")
    @Cacheable("marketRanks")
    public Map<String, Integer> getMarketRanks() {
        try {
            return webClient.get()
                .uri(apiUrl + "/coins/markets?vs_currency=usd&order=market_cap_desc")
                .retrieve()
                .bodyToMono(List.class)
                .flatMapIterable(list -> (List<Map<String, Object>>) list)
                .collectList()
                .<Map<String, Integer>>map(response -> {
                    Map<String, Integer> ranks = new HashMap<>();
                    int rank = 1;
                    
                    for (Map<String, Object> coin : response) {
                        Object symbolObj = coin.get("symbol");
                        if (symbolObj instanceof String) {
                            String symbol = ((String) symbolObj).toUpperCase();
                            ranks.put(symbol, rank++);
                        }
                    }
                    
                    // Update the cache
                    rankCache.clear();
                    rankCache.putAll(ranks);
                    return ranks;
                })
                .onErrorResume(e -> {
                    // Return cached data if available, otherwise empty map
                    if (!rankCache.isEmpty()) {
                        return Mono.just(new HashMap<>(rankCache));
                    }
                    return Mono.just(new HashMap<>());
                })
                .block();
        } catch (Exception e) {
            // Return cached data if available, otherwise empty map
            if (!rankCache.isEmpty()) {
                return new HashMap<>(rankCache);
            }
            return new HashMap<>();
        }
    }
    
    @CacheEvict(value = "marketRanks", allEntries = true)
    @Scheduled(fixedRate = 3600000) // Update every hour
    public void evictMarketRanksCache() {
        // Method to clear the cache periodically
    }
    
    public Integer getRankForSymbol(String symbol) {
        // Try to get from local cache first
        Integer rank = rankCache.get(symbol);
        if (rank != null) {
            return rank;
        }
        
        // If not in local cache, try to get from the main cache
        return getMarketRanks().get(symbol);
    }
}
