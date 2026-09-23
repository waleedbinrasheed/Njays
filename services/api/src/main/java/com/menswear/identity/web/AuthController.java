package com.menswear.identity.web;

import com.menswear.identity.dto.AuthDtos;
import com.menswear.identity.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthDtos.MeResponse register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return authService.register(request, httpRequest, httpResponse);
    }

    @PostMapping("/login")
    public AuthDtos.MeResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return authService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    public AuthDtos.MessageResponse logout(HttpServletRequest request) {
        authService.logout(request);
        return new AuthDtos.MessageResponse("Signed out.");
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me() {
        return authService.me();
    }
}
