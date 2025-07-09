package com.trading212.Trading212.repository;

import com.trading212.Trading212.dto.TransactionHistoryDTO;
import com.trading212.Trading212.model.TransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TransactionRepository {
    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long recordTransaction(Long userId, Long cryptoId, TransactionType type, 
                                BigDecimal quantity, BigDecimal price, 
                                BigDecimal totalAmount, BigDecimal profitLoss) {
        String sql = """
            INSERT INTO transactions (
                user_id, 
                crypto_id, 
                transaction_type, 
                quantity, 
                price, 
                total_amount, 
                profit_loss
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                sql, 
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setLong(2, cryptoId);
            ps.setString(3, type.name());
            ps.setBigDecimal(4, quantity);
            ps.setBigDecimal(5, price);
            ps.setBigDecimal(6, totalAmount);
            ps.setBigDecimal(7, profitLoss);
            return ps;
        }, keyHolder);
        
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }
    
    public List<TransactionHistoryDTO> getTransactionHistory(Long userId, int limit, int offset) {
        String sql = """
            SELECT 
                t.id,
                c.symbol,
                c.name as crypto_name,
                t.transaction_type as type,
                t.quantity,
                t.price,
                t.total_amount as totalAmount,
                t.profit_loss as profitLoss,
                t.timestamp,
                'COMPLETED' as status
            FROM transactions t
            JOIN cryptocurrencies c ON t.crypto_id = c.id
            WHERE t.user_id = ?
            ORDER BY t.timestamp DESC
            LIMIT ? OFFSET ?
            """;
            
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            TransactionHistoryDTO dto = new TransactionHistoryDTO();
            dto.setId(rs.getLong("id"));
            dto.setSymbol(rs.getString("symbol"));
            dto.setCryptoName(rs.getString("crypto_name"));
            dto.setType(TransactionType.valueOf(rs.getString("type")));
            dto.setQuantity(rs.getBigDecimal("quantity"));
            dto.setPrice(rs.getBigDecimal("price"));
            dto.setTotalAmount(rs.getBigDecimal("totalAmount"));
            dto.setProfitLoss(rs.getBigDecimal("profitLoss"));
            dto.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
            dto.setStatus(rs.getString("status"));
            return dto;
        }, userId, limit, offset);
    }
    
    public int getTransactionHistoryCount(Long userId) {
        String sql = "SELECT COUNT(*) FROM transactions WHERE user_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, userId);
    }
}
