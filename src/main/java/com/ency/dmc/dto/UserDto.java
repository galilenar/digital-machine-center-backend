package com.ency.dmc.dto;

import com.ency.dmc.model.UserRole;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String company;
    private UserRole role;
    private LocalDateTime createdAt;
}
