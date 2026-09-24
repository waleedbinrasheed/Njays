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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private Authentication authenticationFor(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private void setRefreshTokenMinutes(long minutes) {
        ReflectionTestUtils.setField(authService, "refreshTokenMinutes", minutes);
    }

    @Test
    void register_createsUserAndIssuesTokens() {
        setRefreshTokenMinutes(30);
        var request = new AuthDtos.RegisterRequest("Jane Doe", "Jane@Example.com", "password123", null);
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        User saved = User.builder().id(5L).fullName("Jane Doe").email("jane@example.com")
                .passwordHash("hashed").role(Role.USER).enabled(true).build();
        when(authenticationManager.authenticate(any())).thenReturn(authenticationFor(saved));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");

        AuthDtos.TokenResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("jane@example.com");
        verify(userRepository).save(any(User.class));
        verify(branchRepository, never()).existsById(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);
        var request = new AuthDtos.RegisterRequest("Jane Doe", "jane@example.com", "password123", null);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsUnknownBranch() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(branchRepository.existsById(42L)).thenReturn(false);
        var request = new AuthDtos.RegisterRequest("Jane Doe", "jane@example.com", "password123", 42L);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_issuesTokensOnValidCredentials() {
        setRefreshTokenMinutes(30);
        User user = User.builder().id(1L).fullName("Jane Doe").email("jane@example.com")
                .passwordHash("hashed").role(Role.ADMIN).branchId(3L).enabled(true).build();
        when(authenticationManager.authenticate(any())).thenReturn(authenticationFor(user));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        var request = new AuthDtos.LoginRequest("jane@example.com", "password123");

        AuthDtos.TokenResponse response = authService.login(request);

        assertThat(response.user().id()).isEqualTo(1L);
        assertThat(response.user().role()).isEqualTo("ADMIN");
        assertThat(response.user().branchId()).isEqualTo(3L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_wrapsAuthenticationFailureAsGenericBadRequest() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));
        var request = new AuthDtos.LoginRequest("jane@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Incorrect email or password");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_rotatesTokenAndIssuesNewAccessToken() {
        setRefreshTokenMinutes(30);
        User user = User.builder().id(1L).fullName("Jane Doe").email("jane@example.com")
                .passwordHash("hashed").role(Role.USER).enabled(true).build();
        RefreshToken existing = RefreshToken.builder().id(9L).token("old-token").userId(1L)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES)).build();
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");

        AuthDtos.TokenResponse response = authService.refresh(new AuthDtos.RefreshRequest("old-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isNotEqualTo("old-token");
        verify(refreshTokenRepository).delete(existing);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_rejectsUnknownToken() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest("missing")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refresh_rejectsExpiredToken() {
        RefreshToken expired = RefreshToken.builder().id(9L).token("expired-token").userId(1L)
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build();
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest("expired-token")))
                .isInstanceOf(BadRequestException.class);

        verify(refreshTokenRepository).delete(expired);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void refresh_rejectsDisabledUser() {
        User disabled = User.builder().id(1L).fullName("Jane").email("jane@example.com")
                .passwordHash("h").role(Role.USER).enabled(false).build();
        RefreshToken existing = RefreshToken.builder().id(9L).token("token").userId(1L)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES)).build();
        when(refreshTokenRepository.findByToken("token")).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest("token")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void logout_deletesMatchingRefreshToken() {
        RefreshToken existing = RefreshToken.builder().id(9L).token("token").userId(1L)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES)).build();
        when(refreshTokenRepository.findByToken("token")).thenReturn(Optional.of(existing));

        authService.logout(new AuthDtos.RefreshRequest("token"));

        verify(refreshTokenRepository).delete(existing);
    }

    @Test
    void logout_isNoOpForUnknownToken() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        authService.logout(new AuthDtos.RefreshRequest("missing"));

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void me_returnsCurrentPrincipalFromSecurityContext() {
        User user = User.builder().id(7L).fullName("Bob").email("bob@example.com")
                .passwordHash("h").role(Role.ADMIN).enabled(true).build();
        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::currentUser).thenReturn(new UserPrincipal(user));

            AuthDtos.MeResponse response = authService.me();

            assertThat(response.id()).isEqualTo(7L);
            assertThat(response.email()).isEqualTo("bob@example.com");
            assertThat(response.role()).isEqualTo("ADMIN");
        }
    }
}
