package com.example.QuanLyChoThueXeDien.auth.services;


import com.example.QuanLyChoThueXeDien.auth.dtos.RegisterRequest;
import com.example.QuanLyChoThueXeDien.auth.entities.Account;
import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import com.example.QuanLyChoThueXeDien.auth.repositories.AccountRepository;
import com.example.QuanLyChoThueXeDien.auth.repositories.CustomerProfileRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class RegistrationService {
        private final AccountRepository accountRepository;
        private final CustomerProfileRepository customerProfileRepository;
        private final PasswordEncoder passwordEncoder;

    public RegistrationService(AccountRepository accountRepository,
                               CustomerProfileRepository customerProfileRepository,
                               PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register (RegisterRequest rq){
        String email= rq.getEmail().trim().toLowerCase(Locale.ROOT);
        String fullname= rq.getFullName().trim();

        if(fullname.length() < 3){
            throw new IllegalArgumentException("Họ tên chứa ít nhất 3 chữ");
        }

        if(!rq.getPassword().equals(rq.getConfirmPassword())){
            throw new IllegalArgumentException("Mật khẩu xác nhận phải trùng với mật khẩu bạn điền");
        }

        if(rq.getPassword().getBytes(StandardCharsets.UTF_8).length > 50){
            throw  new IllegalArgumentException("Mật khẩu quá dài");
        }

        if(accountRepository.existsByEmail(email)){
            throw  new IllegalArgumentException("Email đã được đăng ký");
        }

        Account account= new Account();
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode(rq.getPassword()));
        account.setRole("CUSTOMER");
        account.setStatus("ACTIVE");

        accountRepository.saveAndFlush(account);

        CustomerProfile customerProfile= new CustomerProfile();
        customerProfile.setAccount(account);
        customerProfile.setFullName(fullname);
        customerProfile.setPhone(rq.getPhone().trim());

        customerProfileRepository.saveAndFlush(customerProfile);


    }

}
