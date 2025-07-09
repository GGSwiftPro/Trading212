package com.trading212.Trading212.service;

import com.trading212.Trading212.dto.TradeRequest;
import com.trading212.Trading212.dto.TradeResponse;
import com.trading212.Trading212.dto.TransactionHistoryDTO;
import com.trading212.Trading212.model.CryptoCurrencyEntity;
import com.trading212.Trading212.model.TransactionType;
import com.trading212.Trading212.model.UserEntity;
import com.trading212.Trading212.repository.CryptoRepository;
import com.trading212.Trading212.repository.TransactionRepository;
import com.trading212.Trading212.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TradingService {
    private static final Logger logger = LoggerFactory.getLogger(TradingService.class);
    
    private final UserRepository userRepository;
    private final CryptoRepository cryptoRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    public TradingService(UserRepository userRepository, 
                         CryptoRepository cryptoRepository,
                         TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.cryptoRepository = cryptoRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TradeResponse buyCrypto(TradeRequest request) {
        logger.info("Processing BUY order: {}", request);
        
        // 1. Validate user and get current balance
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // 2. Get current crypto price
        CryptoCurrencyEntity crypto = cryptoRepository.findBySymbol(request.getSymbol())
                .orElseThrow(() -> new IllegalArgumentException("Cryptocurrency not found"));
        
        // 3. Calculate total cost
        BigDecimal totalCost = crypto.getCurrentPrice().multiply(request.getQuantity())
                .setScale(8, RoundingMode.HALF_UP);
        
        // 4. Check if user has sufficient balance
        if (user.getBalance().compareTo(totalCost) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        
        // 5. Update user balance
        BigDecimal newBalance = user.getBalance().subtract(totalCost);
        userRepository.updateBalance(user.getId(), newBalance);
        
        // 6. Update user holdings
        cryptoRepository.updateUserHolding(user.getId(), crypto.getId(), request.getQuantity());
        
        // 7. Record transaction
        Long transactionId = transactionRepository.recordTransaction(
                user.getId(),
                crypto.getId(),
                TransactionType.BUY,
                request.getQuantity(),
                crypto.getCurrentPrice(),
                totalCost,
                null // No profit/loss for buy orders
        );
        
        // 8. Prepare and return response
        return createTradeResponse(
                transactionId,
                crypto,
                TransactionType.BUY,
                request.getQuantity(),
                crypto.getCurrentPrice(),
                totalCost,
                newBalance,
                "Buy order executed successfully"
        );
    }

    @Transactional
    public TradeResponse sellCrypto(TradeRequest request) {
        logger.info("Processing SELL order: {}", request);
        
        // 1. Validate user
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // 2. Get crypto info and current holdings
        CryptoCurrencyEntity crypto = cryptoRepository.findBySymbol(request.getSymbol())
                .orElseThrow(() -> new IllegalArgumentException("Cryptocurrency not found"));
        
        BigDecimal currentHolding = cryptoRepository.getUserHolding(user.getId(), crypto.getId());
        
        // 3. Check if user has sufficient quantity to sell
        if (currentHolding.compareTo(request.getQuantity()) < 0) {
            throw new IllegalStateException("Insufficient quantity to sell");
        }
        
        // 4. Calculate total value and profit/loss
        BigDecimal totalValue = crypto.getCurrentPrice().multiply(request.getQuantity())
                .setScale(8, RoundingMode.HALF_UP);
        
        // 5. Update user balance (add the sale value)
        BigDecimal newBalance = user.getBalance().add(totalValue);
        userRepository.updateBalance(user.getId(), newBalance);
        
        // 6. Update user holdings (subtract the sold quantity)
        BigDecimal newHolding = currentHolding.subtract(request.getQuantity());
        cryptoRepository.updateUserHolding(user.getId(), crypto.getId(), newHolding.negate());
        
        // 7. Calculate profit/loss (simplified - would need to track purchase price for accurate P&L)
        // For demo, we'll skip detailed P&L calculation
        
        // 8. Record transaction
        Long transactionId = transactionRepository.recordTransaction(
                user.getId(),
                crypto.getId(),
                TransactionType.SELL,
                request.getQuantity(),
                crypto.getCurrentPrice(),
                totalValue,
                null // For simplicity, not calculating P&L here
        );
        
        // 9. Prepare and return response
        return createTradeResponse(
                transactionId,
                crypto,
                TransactionType.SELL,
                request.getQuantity(),
                crypto.getCurrentPrice(),
                totalValue,
                newBalance,
                "Sell order executed successfully"
        );
    }
    
    private TradeResponse createTradeResponse(Long transactionId,
                                           CryptoCurrencyEntity crypto,
                                           TransactionType type,
                                           BigDecimal quantity,
                                           BigDecimal price,
                                           BigDecimal totalAmount,
                                           BigDecimal newBalance,
                                           String message) {
        TradeResponse response = new TradeResponse();
        response.setTransactionId(transactionId);
        response.setSymbol(crypto.getSymbol());
        response.setType(type.name());
        response.setQuantity(quantity);
        response.setPrice(price);
        response.setTotalAmount(totalAmount);
        response.setNewBalance(newBalance);
        response.setTimestamp(LocalDateTime.now());
        response.setStatus("COMPLETED");
        response.setMessage(message);
        return response;
    }
    
    public Map<String, Object> getTransactionHistory(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<TransactionHistoryDTO> transactions = transactionRepository.getTransactionHistory(userId, size, offset);
        int total = transactionRepository.getTransactionHistoryCount(userId);
        int totalPages = (int) Math.ceil((double) total / size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("transactions", transactions);
        response.put("currentPage", page);
        response.put("totalItems", total);
        response.put("totalPages", totalPages);
        
        return response;
    }
}
