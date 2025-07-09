package com.trading212.Trading212;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class Trading212Application {

    public static void main(String[] args) {
        SpringApplication.run(Trading212Application.class, args);
    }
}
