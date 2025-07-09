package com.trading212.Trading212.service;

import com.trading212.Trading212.exception.InsufficientFundsException;
import com.trading212.Trading212.exception.UserAlreadyExistsException;
import com.trading212.Trading212.exception.UserNotFoundException;
import com.trading212.Trading212.model.UserEntity;
import com.trading212.Trading212.repository.UserRepo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {
    
    @Autowired
    public UserService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepo userRepo;

    @PostConstruct
    public void initializeDefaultUser() {
        try {
            // Check if default user exists
            Optional<UserEntity> defaultUser = userRepo.findByUsername("default_user");

            if (defaultUser.isEmpty()) {
                logger.info("Creating default user...");
                userRepo.insert("default_user");
                logger.info("Default user created successfully.");
            } else {
                logger.info("Default user already exists.");
            }
        } catch (Exception e) {
            logger.error("Error initializing default user: " + e.getMessage(), e);
        }
    }

    public Optional<UserEntity> getUserById(Long userId) {
        return userRepo.findById(userId);
    }

    public Optional<UserEntity> getUserByUsername(String username) {
        return userRepo.findByUsername(username);
    }
    
    public UserEntity registerUser(String username) {
        try {
            if (userRepo.findByUsername(username).isPresent()) {
                throw new UserAlreadyExistsException("Username already exists: " + username);
            }
            Long userId = userRepo.insert(username);
            return userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve user after creation"));
        } catch (DuplicateKeyException e) {
            throw new UserAlreadyExistsException("Username already exists: " + username);
        }
    }
    
    public UserEntity getUserBalance(Long userId) {
        return userRepo.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }
    
    public UserEntity depositFunds(Long userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        
        UserEntity user = userRepo.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
            
        BigDecimal newBalance = user.getBalance().add(amount);
        userRepo.updateBalance(userId, newBalance);
        user.setBalance(newBalance);
        return user;
    }
    
    public UserEntity withdrawFunds(Long userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        
        UserEntity user = userRepo.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
            
        if (user.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds for withdrawal");
        }
        
        BigDecimal newBalance = user.getBalance().subtract(amount);
        userRepo.updateBalance(userId, newBalance);
        user.setBalance(newBalance);
        return user;
    }
    
    @Transactional(readOnly = true)
    public List<UserEntity> getAllUsers() {
        return userRepo.findAll();
    }

    public void updateUserBalance(Long userId, BigDecimal newBalance) {
        userRepo.updateBalance(userId, newBalance);
    }

    public void resetUserAccount(Long userId) {
        // Reset balance to initial value
        userRepo.resetBalance(userId);
        logger.info("Reset account for user ID: {}", userId);
    }

    public Long createUser(String username) {
        return userRepo.insert(username);
    }
}
