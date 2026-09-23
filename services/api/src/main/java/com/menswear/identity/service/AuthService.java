package com.menswear.identity.service;

import com.menswear.branch.repo.BranchRepository;
import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
import com.menswear.identity.dto.AuthDtos;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String GENERIC_LOGIN_FAILURE = "Incorrect email or password";

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(
            UserRepository userRepository,
            BranchRepository branchRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository
    ) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @Transactional
    public AuthDtos.MeResponse register(AuthDtos.RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
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

        return establishSession(email, request.password(), httpRequest, httpResponse);
    }

    @Transactional(readOnly = true)
    public AuthDtos.MeResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return establishSession(request.email().trim().toLowerCase(), request.password(), httpRequest, httpResponse);
    }

    private AuthDtos.MeResponse establishSession(String email, String password, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authResult;
        try {
            authResult = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException e) {
            // Deliberately generic: don't reveal whether the email itself is registered.
            throw new BadRequestException(GENERIC_LOGIN_FAILURE);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return toMeResponse((UserPrincipal) authResult.getPrincipal());
    }

    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    public AuthDtos.MeResponse me() {
        return toMeResponse(com.menswear.identity.security.SecurityUtils.currentUser());
    }

    private AuthDtos.MeResponse toMeResponse(UserPrincipal principal) {
        return new AuthDtos.MeResponse(principal.getId(), principal.getFullName(), principal.getEmail(), principal.getRole(), principal.getBranchId());
    }
}
