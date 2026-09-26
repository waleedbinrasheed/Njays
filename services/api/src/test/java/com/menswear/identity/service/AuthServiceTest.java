package com.menswear.identity.service;

import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
import com.menswear.config.MenswearProperties;
import com.menswear.identity.dto.AuthDtos;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.PasswordResetTokenRepository;
import com.menswear.identity.repo.RefreshTokenRepository;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.JwtService;
import com.menswear.identity.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final MenswearProperties properties = new MenswearProperties(
            new MenswearProperties.Jwt("secret", 120, 14),
            new MenswearProperties.Cors(List.of("http://localhost:3000")),
            new MenswearProperties.Whatsapp("923001234567"),
            new MenswearProperties.Payments(
                    new MenswearProperties.Bank("Title", "Acct", "Bank", "IBAN"),
                    new MenswearProperties.Jazzcash("MID", "PW", "SALT", "return", "frontendReturn", true, true)
            ),
            new MenswearProperties.Frontend("http://localhost:3000", true),
            new MenswearProperties.Business("NJAY'S by S.A.R", "Address", "+923001234567", "hi@example.com"),
            "PKR"
    );

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository, refreshTokenRepository, passwordResetTokenRepository,
                passwordEncoder, jwtService, authenticationManager, properties
        );
    }

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .fullName("Test User")
                .phone("923001234567")
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
    }

    // ---- register: duplicate phone (the new DB constraint's application-level twin) ----

    @Test
    void registerRejectsDuplicatePhone() {
        var request = new AuthDtos.RegisterRequest("new@example.com", "Password1", "New User", "03001234567");
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("923001234567")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mobile number already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerSkipsPhoneCheckWhenPhoneOmitted() {
        var request = new AuthDtos.RegisterRequest("new@example.com", "Password1", "New User", null);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.createAccessToken(any(), any(), any())).thenReturn("access-token");

        service.register(request);

        verify(userRepository, never()).existsByPhone(any());
    }

    // ---- login: identifier validation ----

    @Test
    void loginRejectsGarbageIdentifier() {
        var request = new AuthDtos.LoginRequest("ab", "password123");
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid email address or mobile number");
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginRejectsMalformedEmail() {
        var request = new AuthDtos.LoginRequest("not-an-email@", "password123");
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid email address or mobile number");
        verifyNoInteractions(authenticationManager);
    }

    // ---- login: identifier classification / normalization ----

    @Test
    void loginWithEmailUsesLowercasedEmailAsLookupKey() {
        stubSuccessfulAuth();

        service.login(new AuthDtos.LoginRequest("User@Example.com", "password123"));

        assertThat(capturedLookupKey()).isEqualTo("user@example.com");
    }

    @Test
    void loginWithPhoneNormalizesBeforeLookup() {
        stubSuccessfulAuth();

        service.login(new AuthDtos.LoginRequest("03001234567", "password123"));

        assertThat(capturedLookupKey()).isEqualTo("923001234567");
    }

    // ---- login: generic failure message (no account-enumeration) ----

    @Test
    void loginFailureIsGenericRegardlessOfCause() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Incorrect email/mobile number or password");
    }

    @Test
    void loginFailureMessageIsIdenticalForEmailAndPhoneIdentifiers() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        String emailFailure = catchMessage(() -> service.login(new AuthDtos.LoginRequest("user@example.com", "wrong")));
        String phoneFailure = catchMessage(() -> service.login(new AuthDtos.LoginRequest("03001234567", "wrong")));

        assertThat(emailFailure).isEqualTo(phoneFailure);
    }

    private void stubSuccessfulAuth() {
        User user = sampleUser();
        UserPrincipal principal = new UserPrincipal(user);
        Authentication authResult = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authResult);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.createAccessToken(any(), any(), any())).thenReturn("access-token");
    }

    private String capturedLookupKey() {
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        return String.valueOf(captor.getValue().getPrincipal());
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }
}
