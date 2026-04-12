package com.fmt.fmt_backend.config;

import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Runs once at startup.
 * Creates the default ADMIN account if no admin exists yet.
 *
 * Set these env vars (add to .env):
 *   ADMIN_SEED_EMAIL=admin@firstmilliontrade.com
 *   ADMIN_SEED_PASSWORD=YourStrongPass@123
 *   ADMIN_SEED_FIRST_NAME=Admin
 *   ADMIN_SEED_LAST_NAME=First Million Trade
 *
 * On every subsequent restart it checks first — if an ADMIN already exists,
 * it does nothing (safe to leave enabled permanently).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.admin.email:admin@firstmilliontrade.com}")
    private String adminEmail;

    @Value("${seed.admin.password:Admin@1234}")
    private String adminPassword;

    @Value("${seed.admin.first-name:Admin}")
    private String adminFirstName;

    @Value("${seed.admin.last-name:First Million Trade}")
    private String adminLastName;

    @Override
    public void run(ApplicationArguments args) {
        long adminCount = userRepository.countByUserRole(UserRole.ADMIN);

        if (adminCount > 0) {
            log.info("Admin account already exists — skipping seed.");
            return;
        }

        User admin = User.builder()
                .firstName(adminFirstName)
                .lastName(adminLastName)
                .email(adminEmail.toLowerCase().trim())
                .password(passwordEncoder.encode(adminPassword))
                .userRole(UserRole.ADMIN)
                .isActive(true)
                .isEmailVerified(true)
                .emailVerifiedAt(LocalDateTime.now())
                .isMobileVerified(true)
                .mobileVerifiedAt(LocalDateTime.now())
                .failedLoginAttempts(0)
                .lastPasswordChangeAt(LocalDateTime.now())
                .build();

        userRepository.save(admin);
        log.info("Default admin created: {}", adminEmail);
    }
}
