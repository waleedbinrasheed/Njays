package com.menswear.config;

import com.menswear.common.enums.Role;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${menswear.admin.email:admin@menswear.local}") String adminEmail,
            @Value("${menswear.admin.password:Admin@12345}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        userRepository.findByEmailIgnoreCase(adminEmail).ifPresentOrElse(
                admin -> log.info("Admin account already present ({})", adminEmail),
                () -> {
                    User admin = User.builder()
                            .fullName("Admin")
                            .email(adminEmail)
                            .passwordHash(passwordEncoder.encode(adminPassword))
                            .role(Role.ADMIN)
                            .enabled(true)
                            .build();
                    userRepository.save(admin);
                    log.info("Created default admin account: {}", adminEmail);
                }
        );
    }
}
