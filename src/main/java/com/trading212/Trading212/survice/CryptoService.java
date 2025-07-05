package com.trading212.Trading212.survice;

import com.trading212.Trading212.model.CryptoCurrencyEntity;
import com.trading212.Trading212.repository.CryptoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
public class CryptoService {

    CryptoRepository cryptoRepo;

    CryptoCurrencyEntity dummyBitcoin = new CryptoCurrencyEntity(
            "BTC",
            "Bitcoin",
            "XBT/USD", // Kraken's pair name for Bitcoin/USD
            new BigDecimal("65000.00") // Example initial price
    );

    public void test()
    {
        cryptoRepo.insert(dummyBitcoin);

    }

}
