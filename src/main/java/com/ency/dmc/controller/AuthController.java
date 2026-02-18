package com.ency.dmc.controller;

import com.ency.dmc.dto.*;
import com.ency.dmc.model.User;
import com.ency.dmc.model.UserRole;
import com.ency.dmc.repository.UserRepository;
import com.ency.dmc.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = Base64.getEncoder().encodeToString(
                (user.getUsername() + ":" + request.getPassword()).getBytes());

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .token(token)
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestParam String username,
                                            @RequestParam String password,
                                            @RequestParam String email,
                                            @RequestParam(required = false) String fullName,
                                            @RequestParam(required = false) String company) {
        UserDto user = userService.create(username, password, email, fullName, company, UserRole.USER);
        return ResponseEntity.ok(user);
    }
}
