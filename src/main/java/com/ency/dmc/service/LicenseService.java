package com.ency.dmc.service;

import com.ency.dmc.dto.LicenseDto;
import com.ency.dmc.model.*;
import com.ency.dmc.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseRepository licenseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Value("${app.default-trial-days:30}")
    private int defaultTrialDays;

    @Transactional
    public LicenseDto issueTrialLicense(Long userId, Long productId) {
        if (licenseRepository.existsByUserIdAndProductId(userId, productId)) {
            License existing = licenseRepository.findByUserIdAndProductId(userId, productId)
                    .orElseThrow();
            return toDto(existing);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        int trialDays = product.getTrialDays() != null ? product.getTrialDays() : defaultTrialDays;

        License license = License.builder()
                .licenseKey(UUID.randomUUID().toString().toUpperCase())
                .product(product)
                .user(user)
                .trial(true)
                .active(true)
                .activatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(trialDays))
                .build();

        license = licenseRepository.save(license);
        return toDto(license);
    }

    @Transactional
    public LicenseDto issuePermanentLicense(Long userId, Long productId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        // Deactivate existing licenses for this user/product
        licenseRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    licenseRepository.save(existing);
                });

        License license = License.builder()
                .licenseKey(UUID.randomUUID().toString().toUpperCase())
                .product(product)
                .user(user)
                .trial(false)
                .active(true)
                .activatedAt(LocalDateTime.now())
                .expiresAt(null)
                .build();

        license = licenseRepository.save(license);
        return toDto(license);
    }

    public List<LicenseDto> getUserLicenses(Long userId) {
        return licenseRepository.findByUserId(userId).stream().map(this::toDto).toList();
    }

    public LicenseDto findByKey(String key) {
        License license = licenseRepository.findByLicenseKey(key)
                .orElseThrow(() -> new RuntimeException("License not found: " + key));
        return toDto(license);
    }

    private LicenseDto toDto(License l) {
        return LicenseDto.builder()
                .id(l.getId())
                .licenseKey(l.getLicenseKey())
                .productId(l.getProduct().getId())
                .productName(l.getProduct().getName())
                .userId(l.getUser().getId())
                .username(l.getUser().getUsername())
                .trial(l.isTrial())
                .active(l.isActive())
                .activatedAt(l.getActivatedAt())
                .expiresAt(l.getExpiresAt())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
