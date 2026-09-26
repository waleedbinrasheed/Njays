package com.menswear.identity.security;

import com.menswear.common.enums.Role;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

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

    @Test
    void routesEmailLookingIdentifierToEmailQuery() {
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(sampleUser()));

        var result = service.loadUserByUsername("user@example.com");

        assertThat(result.getUsername()).isEqualTo("user@example.com");
        verify(userRepository, never()).findByPhone(any());
    }

    @Test
    void routesNonEmailIdentifierToPhoneQuery() {
        when(userRepository.findByPhone("923001234567")).thenReturn(Optional.of(sampleUser()));

        var result = service.loadUserByUsername("923001234567");

        assertThat(result.getUsername()).isEqualTo("user@example.com");
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void throwsWhenIdentifierMatchesNoUser() {
        when(userRepository.findByPhone("000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("000000000"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
