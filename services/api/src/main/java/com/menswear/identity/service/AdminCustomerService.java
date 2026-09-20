package com.menswear.identity.service;

import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
import com.menswear.identity.dto.AdminCustomerDtos;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin-facing customer lookup/creation for in-shop order intake. Walk-in
 * customers get a real account (so they can self-service online later) but
 * with a random, unknown password — see AuthService for why forgot-password
 * currently requires an email, which a phone-only walk-in may not have.
 */
@Service
public class AdminCustomerService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminCustomerService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminCustomerDtos.CustomerSummary> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        String normalizedPhone = AuthService.normalizePhone(q);
        String phoneTerm = (normalizedPhone != null && !normalizedPhone.isEmpty()) ? normalizedPhone : q;
        return userRepository
                .findTop20ByPhoneContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        phoneTerm, q, q
                )
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public AdminCustomerDtos.CustomerSummary createWalkIn(AdminCustomerDtos.CreateWalkInRequest request) {
        String phone = AuthService.normalizePhone(request.phone());
        if (phone == null || phone.length() < 7) {
            throw new BadRequestException("A valid phone number is required");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Mobile number already registered");
        }
        String email = request.email() == null || request.email().isBlank()
                ? null
                : request.email().trim().toLowerCase();
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .fullName(request.fullName().trim())
                .phone(phone)
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
        return toSummary(userRepository.save(user));
    }

    private AdminCustomerDtos.CustomerSummary toSummary(User user) {
        return new AdminCustomerDtos.CustomerSummary(user.getId(), user.getFullName(), user.getPhone(), user.getEmail());
    }
}
