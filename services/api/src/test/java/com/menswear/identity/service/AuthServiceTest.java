package com.menswear.identity.service;

import com.menswear.branch.repo.BranchRepository;
import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
import com.menswear.identity.dto.AuthDtos;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.identity.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private SecurityContextRepository securityContextRepository;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private HttpServletResponse httpResponse;

    @InjectMocks
    private AuthService authService;

    private Authentication authenticationFor(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void register_createsUserAndEstablishesSession() {
        var request = new AuthDtos.RegisterRequest("Jane Doe", "Jane@Example.com", "password123", null);
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        User saved = User.builder().id(5L).fullName("Jane Doe").email("jane@example.com")
                .passwordHash("hashed").role(Role.USER).enabled(true).build();
        when(authenticationManager.authenticate(any())).thenReturn(authenticationFor(saved));

        AuthDtos.MeResponse response = authService.register(request, httpRequest, httpResponse);

        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository).save(any(User.class));
        verify(branchRepository, never()).existsById(any());
        verify(securityContextRepository).saveContext(any(), eq(httpRequest), eq(httpResponse));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);
        var request = new AuthDtos.RegisterRequest("Jane Doe", "jane@example.com", "password123", null);

        assertThatThrownBy(() -> authService.register(request, httpRequest, httpResponse))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsUnknownBranch() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(branchRepository.existsById(42L)).thenReturn(false);
        var request = new AuthDtos.RegisterRequest("Jane Doe", "jane@example.com", "password123", 42L);

        assertThatThrownBy(() -> authService.register(request, httpRequest, httpResponse))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_establishesSessionOnValidCredentials() {
        User user = User.builder().id(1L).fullName("Jane Doe").email("jane@example.com")
                .passwordHash("hashed").role(Role.ADMIN).branchId(3L).enabled(true).build();
        when(authenticationManager.authenticate(any())).thenReturn(authenticationFor(user));
        var request = new AuthDtos.LoginRequest("jane@example.com", "password123");

        AuthDtos.MeResponse response = authService.login(request, httpRequest, httpResponse);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.branchId()).isEqualTo(3L);
        verify(securityContextRepository).saveContext(any(), eq(httpRequest), eq(httpResponse));
    }

    @Test
    void login_wrapsAuthenticationFailureAsGenericBadRequest() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));
        var request = new AuthDtos.LoginRequest("jane@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Incorrect email or password");

        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    void logout_invalidatesExistingSession() {
        HttpSession session = mock(HttpSession.class);
        when(httpRequest.getSession(false)).thenReturn(session);

        authService.logout(httpRequest);

        verify(session).invalidate();
    }

    @Test
    void logout_isNoOpWhenNoSessionExists() {
        when(httpRequest.getSession(false)).thenReturn(null);

        authService.logout(httpRequest);
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
