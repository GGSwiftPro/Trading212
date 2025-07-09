package com.trading212.Trading212.repository;

import com.trading212.Trading212.model.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepo {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public UserRepo(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper for UserEntity
    private static final class UserRowMapper implements RowMapper<UserEntity> {
        @Override
        public UserEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserEntity user = new UserEntity();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setBalance(rs.getBigDecimal("balance"));
            user.setLastUpdated(rs.getTimestamp("last_updated").toLocalDateTime());
            return user;
        }
    }

    public Long insert(String username) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (username, balance) VALUES (?, ?)", 
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, username);
            ps.setBigDecimal(2, new BigDecimal("10000.00"));
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    public Optional<UserEntity> findById(Long id) {
        try {
            UserEntity user = jdbcTemplate.queryForObject(
                "SELECT id, username, balance, last_updated FROM users WHERE id = ?",
                new UserRowMapper(),
                id
            );
            return Optional.ofNullable(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<UserEntity> findByUsername(String username) {
        try {
            UserEntity user = jdbcTemplate.queryForObject(
                "SELECT * FROM users WHERE username = ?",
                new Object[]{username},
                new UserRowMapper()
            );
            return Optional.ofNullable(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    public List<UserEntity> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM users ORDER BY username",
            new UserRowMapper()
        );
    }

    public void updateBalance(Long userId, BigDecimal newBalance) {
        int updated = jdbcTemplate.update(
            "UPDATE users SET balance = ? WHERE id = ?",
            newBalance, userId
        );
        if (updated == 0) {
            throw new RuntimeException("Failed to update user balance. User not found with id: " + userId);
        }
    }

    public void resetBalance(Long userId) {
        jdbcTemplate.update(
            "UPDATE users SET balance = 10000.00, last_updated = CURRENT_TIMESTAMP WHERE id = ?",
            userId
        );
    }

    public boolean deleteUser(Long userId) {
        int rowsAffected = jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        return rowsAffected > 0;
    }
}
