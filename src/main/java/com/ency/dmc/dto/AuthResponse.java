package com.ency.dmc.dto;

import com.ency.dmc.model.UserRole;
import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuthResponse {
    private Long userId;
    private String username;
    private UserRole role;
    private String token;
}
