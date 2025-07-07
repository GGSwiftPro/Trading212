package com.trading212.Trading212.repository;

import com.trading212.Trading212.model.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<UserEntity> userRowMapper;

    @Autowired
    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRowMapper = (rs, rowNum) -> {
            UserEntity user = new UserEntity();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setBalance(rs.getBigDecimal("balance"));
            user.setLastUpdated(rs.getTimestamp("last_updated").toLocalDateTime());
            return user;
        };
    }

    public Optional<UserEntity> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, userRowMapper, id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<UserEntity> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, userRowMapper, username));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional
    public boolean updateBalance(Long userId, BigDecimal newBalance) {
        String sql = "UPDATE users SET balance = ?, last_updated = CURRENT_TIMESTAMP WHERE id = ?";
        int updated = jdbcTemplate.update(sql, newBalance, userId);
        return updated > 0;
    }

    @Transactional
    public UserEntity createUser(String username) {
        String sql = "INSERT INTO users (username, balance) VALUES (?, ?) RETURNING *";
        try {
            return jdbcTemplate.queryForObject(sql, userRowMapper, username, new BigDecimal("10000.00"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create user", e);
        }
    }

    @Transactional
    public UserEntity getOrCreateUser(Long userId) {
        // Try to find existing user
        Optional<UserEntity> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }
        
        // If not found, create a new user with default balance
        return createUser("user_" + userId);
    }
}
