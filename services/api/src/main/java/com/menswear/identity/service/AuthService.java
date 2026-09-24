package com.menswear.identity.service;

import com.menswear.branch.repo.BranchRepository;
import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
import com.menswear.identity.dto.AuthDtos;
import com.menswear.identity.entity.RefreshToken;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.RefreshTokenRepository;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.JwtService;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.identity.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final String GENERIC_LOGIN_FAILURE = "Incorrect email or password";
    private static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenMinutes;

    public AuthService(
            UserRepository userRepository,
            BranchRepository branchRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-token-minutes}") long refreshTokenMinutes
    ) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenMinutes = refreshTokenMinutes;
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Email already registered");
        }
        if (request.branchId() != null && !branchRepository.existsById(request.branchId())) {
            throw new BadRequestException("Branch not found");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .branchId(request.branchId())
                .enabled(true)
                .build();
        userRepository.save(user);

        return issueTokens(authenticate(email, request.password()));
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        return issueTokens(authenticate(request.email().trim().toLowerCase(), request.password()));
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshToken existing = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BadRequestException(SESSION_EXPIRED));
        refreshTokenRepository.delete(existing);
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException(SESSION_EXPIRED);
        }
        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new BadRequestException(SESSION_EXPIRED));
        if (!user.isEnabled()) {
            throw new BadRequestException(SESSION_EXPIRED);
        }
        return issueTokens(new UserPrincipal(user));
    }

    @Transactional
    public void logout(AuthDtos.RefreshRequest request) {
        refreshTokenRepository.findByToken(request.refreshToken()).ifPresent(refreshTokenRepository::delete);
    }

    public AuthDtos.MeResponse me() {
        return toMeResponse(SecurityUtils.currentUser());
    }

    private UserPrincipal authenticate(String email, String password) {
        try {
            var authResult = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
            return (UserPrincipal) authResult.getPrincipal();
        } catch (AuthenticationException e) {
            // Deliberately generic: don't reveal whether the email itself is registered.
            throw new BadRequestException(GENERIC_LOGIN_FAILURE);
        }
    }

    private AuthDtos.TokenResponse issueTokens(UserPrincipal principal) {
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .userId(principal.getId())
                .expiresAt(Instant.now().plus(refreshTokenMinutes, ChronoUnit.MINUTES))
                .build());
        return new AuthDtos.TokenResponse(accessToken, refreshToken, toMeResponse(principal));
    }

    private AuthDtos.MeResponse toMeResponse(UserPrincipal principal) {
        return new AuthDtos.MeResponse(principal.getId(), principal.getFullName(), principal.getEmail(), principal.getRole(), principal.getBranchId());
    }
}
