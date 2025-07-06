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

    public Optional<CryptoCurrencyEntity> findByKrakenPairName(String krakenPairName) {
        final String sql = "SELECT id, symbol, name, kraken_pair_name, current_price, last_updated FROM cryptocurrencies WHERE kraken_pair_name = ?";
        List<CryptoCurrencyEntity> cryptos = jdbcTemplate.query(sql, new CryptocurrencyRowMapper(), krakenPairName);
        return cryptos.stream().findFirst();
    }

    public List<CryptoCurrencyEntity> findAll() {
        try {
            logger.info("Attempting to fetch all cryptocurrencies from database...");
            
            // First check if the table exists
            if (!tableExists("cryptocurrencies")) {
                logger.warn("cryptocurrencies table does not exist!");
                
                // Log database schema for debugging
                try {
                    logger.info("Listing all tables in the database...");
                    List<String> tables = jdbcTemplate.queryForList(
                        "SHOW TABLES", String.class);
                    logger.info("Available tables: {}", tables);
                } catch (Exception e) {
                    logger.error("Failed to list tables: {}", e.getMessage());
                }
                
                return List.of();
            }
            
            // First try a simple count query
            int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cryptocurrencies", Integer.class);
            logger.info("Found {} cryptocurrency records in the database", count);
            
            // If count is 0, return empty list early
            if (count == 0) {
                logger.info("No cryptocurrency records found in the database");
                return List.of();
            }
            
            // Now fetch the actual data
            final String sql = "SELECT id, symbol, name, kraken_pair_name, current_price, last_updated FROM cryptocurrencies";
            logger.debug("Executing query: {}", sql);
            
            List<CryptoCurrencyEntity> result = jdbcTemplate.query(sql, new CryptocurrencyRowMapper());
            logger.info("Successfully retrieved {} cryptocurrency records", result.size());
            
            // Log first few records for debugging
            if (!result.isEmpty()) {
                int logCount = Math.min(result.size(), 3);
                logger.info("Sample records (first {}): {}", 
                    logCount,
                    result.subList(0, logCount).stream()
                        .map(c -> String.format("%s (%s) = $%.2f", 
                            c.getSymbol(), c.getName(), c.getCurrentPrice()))
                        .toList()
                );
            }
            
            return result;
            
        } catch (Exception e) {
            String errorMsg = "Error fetching all cryptocurrencies: " + e.getMessage();
            logger.error(errorMsg, e);
            
            // Log database connection info
            try {
                String dbName = jdbcTemplate.queryForObject(
                    "SELECT DATABASE()", String.class);
                logger.info("Current database: {}", dbName);
            } catch (Exception ex) {
                logger.error("Failed to get current database name: {}", ex.getMessage());
            }
            
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    private boolean tableExists(String tableName) {
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
