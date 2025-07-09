package com.trading212.Trading212.repository;

import com.trading212.Trading212.model.CryptoCurrencyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.math.RoundingMode;

@Repository
public class CryptoRepository {
    private static final Logger logger = LoggerFactory.getLogger(CryptoRepository.class);
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CryptoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper to convert a ResultSet row into a CryptocurrencyEntity object
    private static final class CryptocurrencyRowMapper implements RowMapper<CryptoCurrencyEntity> {
        @Override
        public CryptoCurrencyEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            CryptoCurrencyEntity crypto = new CryptoCurrencyEntity();
            crypto.setId(rs.getLong("id"));
            crypto.setSymbol(rs.getString("symbol"));
            crypto.setName(rs.getString("name"));
            crypto.setKrakenPairName(rs.getString("kraken_pair_name"));
            crypto.setCurrentPrice(rs.getBigDecimal("current_price"));
            // Handle nullable timestamp if necessary, though your DDL has a default
            crypto.setLastUpdated(rs.getTimestamp("last_updated") != null ?
                    rs.getTimestamp("last_updated").toLocalDateTime() : null);
            return crypto;
        }
    }

    /**
     * Inserts a new cryptocurrency into the database.
     */
    @Transactional
    public void ensureTableExists() {
        if (!tableExists("cryptocurrencies")) {
            logger.info("Creating cryptocurrencies table...");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cryptocurrencies (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    symbol VARCHAR(10) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    kraken_pair_name VARCHAR(20) NOT NULL UNIQUE,
                    current_price DECIMAL(20, 8) NOT NULL,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
            logger.info("cryptocurrencies table created successfully");
        }
    }
    
    @Transactional
    public void insert(String symbol, String name, String krakenPairName, Double price) {
        try {
            logger.info("Attempting to insert: {} - {} ({}), pair: {}", symbol, name, price, krakenPairName);
            
            // First check if a record with the same symbol exists
            String checkSymbolSql = "SELECT COUNT(*) FROM cryptocurrencies WHERE symbol = ?";
            int symbolCount = jdbcTemplate.queryForObject(checkSymbolSql, Integer.class, symbol);
            
            // Then check if a record with the same kraken pair exists
            String checkPairSql = "SELECT COUNT(*) FROM cryptocurrencies WHERE kraken_pair_name = ?";
            int pairCount = jdbcTemplate.queryForObject(checkPairSql, Integer.class, krakenPairName);
            
            if (symbolCount > 0) {
                logger.warn("Skipping insert: A cryptocurrency with symbol '{}' already exists.", symbol);
                return;
            }
            
            if (pairCount > 0) {
                logger.warn("Skipping insert: A cryptocurrency with pair name '{}' already exists.", krakenPairName);
                return;
            }
            
            final String sql = "INSERT INTO cryptocurrencies (symbol, name, kraken_pair_name, current_price) VALUES (?, ?, ?, ?)";
            logger.debug("Executing SQL: {} with params: {}, {}, {}, {}", sql, symbol, name, krakenPairName, price);
            
            int rowsAffected = jdbcTemplate.update(sql, symbol, name, krakenPairName, price);
            logger.info("Successfully inserted {} - {} ({}), rows affected: {}", symbol, name, price, rowsAffected);
            
            // Verify the insert
            if (rowsAffected == 0) {
                logger.error("Failed to insert cryptocurrency: {} - {}. No rows affected.", symbol, name);
                throw new RuntimeException("Failed to insert cryptocurrency: " + symbol);
            }
            
            // Double check the insert worked
            String verifySql = "SELECT COUNT(*) FROM cryptocurrencies WHERE symbol = ?";
            int verifyCount = jdbcTemplate.queryForObject(verifySql, Integer.class, symbol);
            logger.info("Verification: Found {} records with symbol {}", verifyCount, symbol);
            
        } catch (Exception e) {
            String errorMsg = String.format("Error inserting cryptocurrency %s - %s: %s", symbol, name, e.getMessage());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void updatePrice(String krakenPairName, BigDecimal newPrice) {
        final String sql = "UPDATE cryptocurrencies SET current_price = ?, last_updated = CURRENT_TIMESTAMP WHERE kraken_pair_name = ?";
        jdbcTemplate.update(sql, newPrice, krakenPairName);
    }
    
    public Optional<CryptoCurrencyEntity> findBySymbol(String symbol) {
        final String sql = "SELECT id, symbol, name, kraken_pair_name, current_price, last_updated FROM cryptocurrencies WHERE symbol = ?";
        List<CryptoCurrencyEntity> cryptos = jdbcTemplate.query(sql, new CryptocurrencyRowMapper(), symbol);
        return cryptos.stream().findFirst();
    }
    
    /**
     * Updates a user's cryptocurrency holding by adding the specified quantity
     * @param userId the ID of the user
     * @param cryptoId the ID of the cryptocurrency
     * @param quantity the quantity to add (can be negative to reduce holding)
     */
    @Transactional
    public void updateUserHolding(Long userId, Long cryptoId, BigDecimal quantity) {
        if (userId == null || cryptoId == null || quantity == null) {
            throw new IllegalArgumentException("User ID, crypto ID, and quantity must not be null");
        }
        
        ensureUserHoldingsTableExists();
        
        if (userHasExistingHolding(userId, cryptoId)) {
            updateExistingHolding(userId, cryptoId, quantity);
        } else {
            insertNewHolding(userId, cryptoId, quantity);
        }
    }
    
    /**
     * Checks if a user has an existing holding for a specific cryptocurrency
     */
    private boolean userHasExistingHolding(Long userId, Long cryptoId) {
        String checkSql = "SELECT COUNT(*) FROM user_holdings WHERE user_id = ? AND crypto_id = ?";
        int count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId, cryptoId);
        return count > 0;
    }
    
    /**
     * Updates an existing user holding by adding the specified quantity
     */
    private void updateExistingHolding(Long userId, Long cryptoId, BigDecimal quantity) {
        String updateSql = """
            UPDATE user_holdings 
            SET quantity = GREATEST(0, quantity + ?) 
            WHERE user_id = ? AND crypto_id = ?
            """;
        
        int updated = jdbcTemplate.update(updateSql, quantity, userId, cryptoId);
        if (updated == 0) {
            logger.warn("No rows were updated for user {} and crypto {}", userId, cryptoId);
        } else {
            logger.debug("Updated holding for user {} and crypto {} by {}", userId, cryptoId, quantity);
        }
    }
    
    /**
     * Inserts a new user holding with the specified quantity
     */
    private void insertNewHolding(Long userId, Long cryptoId, BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            logger.debug("Skipping insert for zero/negative quantity: user={}, crypto={}, quantity={}", 
                userId, cryptoId, quantity);
            return;
        }
        
        String insertSql = """
            INSERT INTO user_holdings (user_id, crypto_id, quantity) 
            VALUES (?, ?, ?)
            """;
        
        try {
            int inserted = jdbcTemplate.update(insertSql, userId, cryptoId, quantity);
            if (inserted > 0) {
                logger.info("Created new holding: user={}, crypto={}, quantity={}", 
                    userId, cryptoId, quantity);
            }
        } catch (Exception e) {
            logger.error("Failed to insert new holding for user {} and crypto {}: {}", 
                userId, cryptoId, e.getMessage());
            throw e;
        }
    }
    
    public BigDecimal getUserHolding(Long userId, Long cryptoId) {
        ensureUserHoldingsTableExists();
        
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM user_holdings WHERE user_id = ? AND crypto_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, userId, cryptoId)
                    .setScale(8, RoundingMode.HALF_UP);
        } catch (Exception e) {
            logger.error("Error getting user holding", e);
            return BigDecimal.ZERO;
        }
    }
    
    private void ensureUserHoldingsTableExists() {
        if (!tableExists("user_holdings")) {
            logger.info("Creating user_holdings table...");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_holdings (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    crypto_id BIGINT NOT NULL,
                    quantity DECIMAL(20, 8) NOT NULL,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_user_crypto (user_id, crypto_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (crypto_id) REFERENCES cryptocurrencies(id) ON DELETE CASCADE
                )
                """);
            logger.info("user_holdings table created successfully");
        }
    }

    public Optional<CryptoCurrencyEntity> findByKrakenPairName(String krakenPairName) {
        final String sql = "SELECT id, symbol, name, kraken_pair_name, current_price, last_updated FROM cryptocurrencies WHERE kraken_pair_name = ?";
        List<CryptoCurrencyEntity> cryptos = jdbcTemplate.query(sql, new CryptocurrencyRowMapper(), krakenPairName);
        return cryptos.stream().findFirst();
    }

    /**
     * Finds all cryptocurrencies with pagination support
     * @param limit maximum number of records to return
     * @param offset number of records to skip
     * @return list of cryptocurrencies
     */
    public List<CryptoCurrencyEntity> findAll(int limit, int offset) {
        try {
            logger.info("Fetching up to {} cryptocurrencies starting from offset {}...", limit, offset);
            
            if (!tableExists("cryptocurrencies")) {
                logger.warn("cryptocurrencies table does not exist!");
                logAvailableTables();
                return List.of();
            }
            
            final String countSql = "SELECT COUNT(*) FROM cryptocurrencies";
            int totalCount = jdbcTemplate.queryForObject(countSql, Integer.class);
            logger.info("Found {} total cryptocurrency records in the database", totalCount);
            
            if (totalCount == 0) {
                logger.info("No cryptocurrency records found in the database");
                return List.of();
            }
            
            final String sql = """
                SELECT id, symbol, name, kraken_pair_name, current_price, last_updated 
                FROM cryptocurrencies 
                ORDER BY symbol 
                LIMIT ? OFFSET ?
                """;
                
            logger.debug("Executing query: {} with limit={}, offset={}", sql, limit, offset);
            
            List<CryptoCurrencyEntity> result = jdbcTemplate.query(
                sql, 
                new CryptocurrencyRowMapper(),
                limit,
                offset
            );
            
            logRetrievedRecords(result);
            return result;
            
        } catch (Exception e) {
            String errorMsg = String.format("Error fetching cryptocurrencies (limit=%d, offset=%d): %s", 
                limit, offset, e.getMessage());
            logDatabaseError(e, errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * Finds all cryptocurrencies without pagination (use with caution for large datasets)
     * @return list of all cryptocurrencies
     */
    public List<CryptoCurrencyEntity> findAll() {
        // Default to a reasonable page size for backward compatibility
        return findAll(1000, 0);
    }
    
    /**
     * Logs information about available tables in the database
     */
    private void logAvailableTables() {
        try {
            logger.info("Listing all tables in the database...");
            List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", 
                String.class
            );
            logger.info("Available tables: {}", tables);
        } catch (Exception e) {
            logger.error("Failed to list tables: {}", e.getMessage());
        }
    }
    
    /**
     * Logs information about retrieved cryptocurrency records
     */
    private void logRetrievedRecords(List<CryptoCurrencyEntity> records) {
        if (!records.isEmpty()) {
            int logCount = Math.min(records.size(), 3);
            logger.info("Retrieved {} cryptocurrency records (showing first {}): {}", 
                records.size(),
                logCount,
                records.subList(0, logCount).stream()
                    .map(c -> String.format("%s ($%.2f)", c.getSymbol(), c.getCurrentPrice()))
                    .toList()
            );
        } else {
            logger.info("No cryptocurrency records found");
        }
    }
    
    /**
     * Logs database connection errors with additional context
     */
    private void logDatabaseError(Exception e, String errorMsg) {
        logger.error(errorMsg, e);
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            logger.info("Current database: {}", dbName);
        } catch (Exception ex) {
            logger.error("Failed to get current database name: {}", ex.getMessage());
        }
    }
    
    public boolean tableExists(String tableName) {
        try {
            logger.info("Checking if table {} exists...", tableName);
            
            // First check information_schema for the table
            String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            int count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
            
            if (count > 0) {
                logger.info("Table {} exists in information_schema", tableName);
                
                // Check if we can actually query the table
                try {
                    jdbcTemplate.queryForObject(
                        "SELECT 1 FROM " + tableName + " LIMIT 1", 
                        Integer.class
                    );
                    logger.info("Successfully queried table {}", tableName);
                    return true;
                } catch (Exception e) {
                    logger.error("Table {} exists but cannot be queried: {}", tableName, e.getMessage());
                    return false;
                }
            } else {
                logger.warn("Table {} does not exist in the current database", tableName);
                
                // List all tables for debugging
                try {
                    List<String> tables = jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", 
                        String.class
                    );
                    logger.info("Available tables in database: {}", tables);
                } catch (Exception e) {
                    logger.error("Failed to list tables: {}", e.getMessage());
                }
                
                return false;
            }
        } catch (Exception e) {
            logger.error("Error checking if table {} exists: {}", tableName, e.getMessage());
            
            // Try to get database name for more context
            try {
                String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
                logger.info("Current database: {}", dbName);
            } catch (Exception ex) {
                logger.error("Failed to get current database name: {}", ex.getMessage());
            }
            
            return false;
        }
    }
}
