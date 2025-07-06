package com.trading212.Trading212.service;

import com.trading212.Trading212.model.UserEntity;
import com.trading212.Trading212.repository.UserRepo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
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
