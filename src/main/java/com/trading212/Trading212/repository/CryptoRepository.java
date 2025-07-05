package com.trading212.Trading212.repository;

import com.trading212.Trading212.model.CryptoCurrencyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class CryptoRepository {

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
    public CryptoCurrencyEntity insert(CryptoCurrencyEntity crypto) {

        final String sql = "INSERT INTO cryptocurrencies (symbol, name, kraken_pair_name, current_price) VALUES (?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                crypto.getSymbol(),
                crypto.getName(),
                crypto.getKrakenPairName(),
                crypto.getCurrentPrice());

        return crypto;
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

    /**
     * Retrieves all cryptocurrencies from the database.
     *
     * @return A list of all CryptocurrencyEntity objects.
     */
    public List<CryptoCurrencyEntity> findAll() {
        final String sql = "SELECT id, symbol, name, kraken_pair_name, current_price, last_updated FROM cryptocurrencies";
        return jdbcTemplate.query(sql, new CryptocurrencyRowMapper());
    }
}
