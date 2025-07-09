package com.trading212.Trading212.controller;

import com.trading212.Trading212.model.UserEntity;
import com.trading212.Trading212.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        return userService.getUserById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser() {
        // For now, return the default user (in a real app, this would be the authenticated user)
        Optional<UserEntity> userOptional = userService.getUserByUsername("default_user");
        if (userOptional.isPresent()) {
            return ResponseEntity.ok(userOptional.get());
        } else {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Default user not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    @PutMapping("/{userId}/reset")
    public ResponseEntity<?> resetUserAccount(@PathVariable Long userId) {
        try {
            userService.resetUserAccount(userId);
            // Return the updated user data
            return userService.getUserById(userId)
                    .map(user -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("message", "Account reset successfully");
                        response.put("user", user);
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestParam String username) {
        try {
            UserEntity newUser = userService.registerUser(username);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "User registered successfully");
            response.put("user", newUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }
    }
    
    @GetMapping("/{userId}/balance")
    public ResponseEntity<?> getUserBalance(@PathVariable Long userId) {
        try {
            UserEntity user = userService.getUserBalance(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("balance", user.getBalance());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }
    
    @PostMapping("/{userId}/deposit")
    public ResponseEntity<?> depositFunds(
            @PathVariable Long userId,
            @RequestParam BigDecimal amount) {
        try {
            UserEntity user = userService.depositFunds(userId, amount);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Funds deposited successfully");
            response.put("balance", user.getBalance());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @PostMapping("/{userId}/withdraw")
    public ResponseEntity<?> withdrawFunds(
            @PathVariable Long userId,
            @RequestParam BigDecimal amount) {
        try {
            UserEntity user = userService.withdrawFunds(userId, amount);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Funds withdrawn successfully");
            response.put("balance", user.getBalance());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        try {
            List<UserEntity> users = userService.getAllUsers();
            Map<String, Object> response = new HashMap<>();
            response.put("users", users);
            response.put("count", users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve users");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
