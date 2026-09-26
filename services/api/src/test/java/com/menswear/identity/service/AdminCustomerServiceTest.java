package com.menswear.identity.service;

import com.menswear.common.exception.BadRequestException;
import com.menswear.identity.dto.AdminCustomerDtos;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCustomerServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminCustomerService service = new AdminCustomerService(userRepository, passwordEncoder);

    @Test
    void searchNormalizesPhoneBeforeQuerying() {
        when(userRepository.findTop20ByPhoneContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                "923001234567", "03001234567", "03001234567"
        )).thenReturn(List.of());

        service.search("03001234567");

        verify(userRepository).findTop20ByPhoneContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                "923001234567", "03001234567", "03001234567"
        );
    }

    @Test
    void searchFallsBackToRawQueryWhenNotPhoneShaped() {
        when(userRepository.findTop20ByPhoneContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                "Ayesha", "Ayesha", "Ayesha"
        )).thenReturn(List.of());

        service.search("Ayesha");

        verify(userRepository).findTop20ByPhoneContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                "Ayesha", "Ayesha", "Ayesha"
        );
    }

    @Test
    void blankQueryReturnsEmptyWithoutHittingTheDatabase() {
        List<AdminCustomerDtos.CustomerSummary> result = service.search("   ");

        assertThat(result).isEmpty();
        verify(userRepository, never())
                .findTop20ByPhoneContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void createsWalkInWithoutEmail() {
        var request = new AdminCustomerDtos.CreateWalkInRequest("Ayesha Khan", "03001234567", null);
        when(userRepository.existsByPhone("923001234567")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        AdminCustomerDtos.CustomerSummary result = service.createWalkIn(request);

        assertThat(result.fullName()).isEqualTo("Ayesha Khan");
        assertThat(result.phone()).isEqualTo("923001234567");
        assertThat(result.email()).isNull();
        verify(userRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void rejectsDuplicatePhoneOnWalkInCreate() {
        var request = new AdminCustomerDtos.CreateWalkInRequest("Ayesha Khan", "03001234567", null);
        when(userRepository.existsByPhone("923001234567")).thenReturn(true);

        assertThatThrownBy(() -> service.createWalkIn(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mobile number already registered");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateEmailOnWalkInCreate() {
        var request = new AdminCustomerDtos.CreateWalkInRequest("Ayesha Khan", "03001234567", "ayesha@example.com");
        when(userRepository.existsByPhone("923001234567")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("ayesha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createWalkIn(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email already registered");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsUnparsablePhone() {
        var request = new AdminCustomerDtos.CreateWalkInRequest("Ayesha Khan", "abc", null);

        assertThatThrownBy(() -> service.createWalkIn(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid phone number");
    }
}
