package com.menswear.identity.service;

import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.dto.UserDtos;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserSummary> listAll() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public UserDtos.UserSummary setEnabled(Long userId, boolean enabled, Long actingAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getId().equals(actingAdminId) && !enabled) {
            throw new BadRequestException("You can't disable your own account");
        }
        user.setEnabled(enabled);
        return toDto(userRepository.save(user));
    }

    private UserDtos.UserSummary toDto(User u) {
        return new UserDtos.UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(), u.getBranchId(), u.isEnabled());
    }
}
