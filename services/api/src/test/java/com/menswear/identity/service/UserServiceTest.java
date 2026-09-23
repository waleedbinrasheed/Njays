package com.menswear.identity.service;

import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.dto.UserDtos;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).fullName("Jane Doe").email("jane@example.com")
                .passwordHash("hash").role(Role.USER).enabled(true).build();
    }

    @Test
    void listAll_mapsUsersToSummaries() {
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(user));

        List<UserDtos.UserSummary> result = userService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("jane@example.com");
        assertThat(result.get(0).role()).isEqualTo("USER");
    }

    @Test
    void setEnabled_disablesAnotherUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDtos.UserSummary result = userService.setEnabled(1L, false, 2L);

        assertThat(result.enabled()).isFalse();
    }

    @Test
    void setEnabled_rejectsSelfDisable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.setEnabled(1L, false, 1L))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_allowsSelfEnable() {
        user.setEnabled(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDtos.UserSummary result = userService.setEnabled(1L, true, 1L);

        assertThat(result.enabled()).isTrue();
    }

    @Test
    void setEnabled_throwsWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setEnabled(99L, true, 1L))
                .isInstanceOf(NotFoundException.class);
    }
}
