package com.trading212.Trading212.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepo {

    private JdbcTemplate jdbcTemplate;

    @Autowired
    public UserRepo(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String name) {
        jdbcTemplate.update("INSERT INTO users (username,balance) values  (?,?)", name, 10000);
    }

}
