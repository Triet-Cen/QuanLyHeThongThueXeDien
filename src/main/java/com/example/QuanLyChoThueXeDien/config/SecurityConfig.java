package com.example.QuanLyChoThueXeDien.config;

import com.example.QuanLyChoThueXeDien.auth.services.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomUserDetailsService customUserDetailsService,
            PasswordEncoder passwordEncoder
    ) throws Exception {
        DaoAuthenticationProvider provider= new DaoAuthenticationProvider(customUserDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        http
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/video/**",
                                "/home",
                                "/vehicles"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/payments/vnpay/ipn").permitAll()
                        .requestMatchers("/admin","/admin/**").hasRole("ADMIN")
                        .requestMatchers("/staff","/staff/**").hasRole("STAFF")
                        .requestMatchers("/bookings", "/bookings/**")
                        .hasRole("CUSTOMER")
                        .requestMatchers("/profile", "/profile/**").hasRole("CUSTOMER")
                        .requestMatchers("/models", "/models/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/after-login", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );
        return http.build();

    }
}
