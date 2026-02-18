package com.ency.dmc.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LicenseDto {
    private Long id;
    private String licenseKey;
    private Long productId;
    private String productName;
    private Long userId;
    private String username;
    private boolean trial;
    private boolean active;
    private LocalDateTime activatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
