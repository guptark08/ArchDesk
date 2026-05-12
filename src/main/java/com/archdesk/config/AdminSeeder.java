package com.archdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.archdesk.user.AppUser;
import com.archdesk.user.AppUserRepository;

@Component
public class AdminSeeder implements CommandLineRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminSeeder(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String email,
            @Value("${app.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new AppUser(email, passwordEncoder.encode(password)));
        }
    }
}
