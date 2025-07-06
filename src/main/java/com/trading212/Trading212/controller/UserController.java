package com.trading212.Trading212.controller;

import com.trading212.Trading212.model.UserEntity;
import com.trading212.Trading212.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
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

    @PutMapping("/{userId}/reset")
    public ResponseEntity<?> resetUserAccount(@PathVariable Long userId) {
        return userService.getUserById(userId)
                .map(user -> {
                    userService.resetUserAccount(userId);
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Account reset successfully");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser() {
        // For simplicity, we're using the default user as the current user
        Optional<UserEntity> userOptional = userService.getUserByUsername("default_user");
        if (userOptional.isPresent()) {
            return ResponseEntity.ok(userOptional.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Default user not found");
        }
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<?> getUserBalance(@PathVariable Long userId) {
        return userService.getUserById(userId)
                .map(user -> {
                    Map<String, BigDecimal> response = new HashMap<>();
                    response.put("balance", user.getBalance());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
