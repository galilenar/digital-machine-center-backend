package com.ency.dmc.controller;

import com.ency.dmc.dto.LicenseDto;
import com.ency.dmc.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @PostMapping("/trial")
    public ResponseEntity<LicenseDto> issueTrial(@RequestParam Long userId,
                                                  @RequestParam Long productId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(licenseService.issueTrialLicense(userId, productId));
    }

    @PostMapping("/permanent")
    public ResponseEntity<LicenseDto> issuePermanent(@RequestParam Long userId,
                                                      @RequestParam Long productId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(licenseService.issuePermanentLicense(userId, productId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LicenseDto>> getUserLicenses(@PathVariable Long userId) {
        return ResponseEntity.ok(licenseService.getUserLicenses(userId));
    }

    @GetMapping("/verify/{key}")
    public ResponseEntity<LicenseDto> verifyLicense(@PathVariable String key) {
        return ResponseEntity.ok(licenseService.findByKey(key));
    }
}
