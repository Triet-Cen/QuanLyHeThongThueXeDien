package com.example.QuanLyChoThueXeDien.auth.services;

import com.example.QuanLyChoThueXeDien.auth.entities.Account;
import com.example.QuanLyChoThueXeDien.auth.repositories.AccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public CustomUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Không tìm thấy tài khoản")
                );

        return User.withUsername(account.getEmail())
                .password(account.getPassword())
                .roles(account.getRole().toUpperCase(Locale.ROOT))
                .disabled(!"ACTIVE".equalsIgnoreCase(account.getStatus()))
                .build();
    }
}
