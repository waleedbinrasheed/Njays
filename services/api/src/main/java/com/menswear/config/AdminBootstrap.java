package com.menswear.config;

import com.menswear.common.enums.Role;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByEmailIgnoreCase("admin@menswear.local").ifPresentOrElse(user -> {
            user.setPasswordHash(passwordEncoder.encode("Admin@12345"));
            user.setRole(Role.ADMIN);
            user.setEnabled(true);
            userRepository.save(user);
            log.info("Ensured admin user password is Admin@12345");
        }, () -> {
            User admin = User.builder()
                    .email("admin@menswear.local")
                    .passwordHash(passwordEncoder.encode("Admin@12345"))
                    .fullName("Store Admin")
                    .phone("923001234567")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("Created admin user admin@menswear.local / Admin@12345");
        });
    }
}
