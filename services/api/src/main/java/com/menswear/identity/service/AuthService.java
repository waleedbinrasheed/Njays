package com.menswear.identity.service;

import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
import com.menswear.config.MenswearProperties;
import com.menswear.identity.dto.AuthDtos;
import com.menswear.identity.entity.PasswordResetToken;
import com.menswear.identity.entity.RefreshToken;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.PasswordResetTokenRepository;
import com.menswear.identity.repo.RefreshTokenRepository;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.JwtService;
import com.menswear.identity.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long RESET_TOKEN_HOURS = 1;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final MenswearProperties properties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            MenswearProperties properties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.properties = properties;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Email already registered");
        }
        User user = User.builder()
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phone(normalizePhone(request.phone()))
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password())
        );
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Refresh token expired");
        }
        stored.setRevoked(true);
        User user = userRepository.findById(stored.getUserId()).orElseThrow();
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.ForgotPasswordResponse forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        String generic = "If an account exists for that email, a reset link has been created.";

        var userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            return new AuthDtos.ForgotPasswordResponse(generic, null);
        }

        User user = userOpt.get();
        passwordResetTokenRepository.markAllUsedForUser(user.getId());

        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().plusSeconds(RESET_TOKEN_HOURS * 3600))
                .used(false)
                .build();
        passwordResetTokenRepository.save(token);

        String appUrl = properties.frontend() != null && properties.frontend().appUrl() != null
                ? properties.frontend().appUrl().replaceAll("/$", "")
                : "http://localhost:3000";
        String resetLink = appUrl + "/reset-password?token=" + rawToken;
        log.info("Password reset link for {}: {}", email, resetLink);

        boolean expose = properties.frontend() == null || properties.frontend().exposeResetLink();
        return new AuthDtos.ForgotPasswordResponse(generic, expose ? resetLink : null);
    }

    @Transactional
    public AuthDtos.MessageResponse resetPassword(AuthDtos.ResetPasswordRequest request) {
        String hash = sha256(request.token().trim());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashAndUsedFalse(hash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Reset link has expired. Please request a new one.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.markAllUsedForUser(user.getId());
        refreshTokenRepository.revokeAllForUser(user.getId());

        return new AuthDtos.MessageResponse("Password updated. You can sign in with your new password.");
    }

    private AuthDtos.AuthResponse issueTokens(User user) {
        String access = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken entity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(refresh))
                .expiresAt(Instant.now().plusSeconds(properties.jwt().refreshTokenDays() * 24 * 3600))
                .revoked(false)
                .build();
        refreshTokenRepository.save(entity);
        return new AuthDtos.AuthResponse(
                access,
                refresh,
                "Bearer",
                new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole().name())
        );
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            digits = "92" + digits.substring(1);
        }
        return digits;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
