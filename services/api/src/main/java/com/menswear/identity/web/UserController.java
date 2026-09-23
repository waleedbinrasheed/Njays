package com.menswear.identity.web;

import com.menswear.identity.dto.UserDtos;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.identity.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDtos.UserSummary> list() {
        return userService.listAll();
    }

    @PatchMapping("/{id}/enabled")
    public UserDtos.UserSummary setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return userService.setEnabled(id, enabled, SecurityUtils.currentUserId());
    }
}
