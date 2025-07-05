package com.trading212.Trading212.survice;

import com.trading212.Trading212.repository.CryptoRepository;
import com.trading212.Trading212.repository.UserRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;


    @PostConstruct
    public void test()
    {
       // userRepo.insert("Vasko");

    }
}
