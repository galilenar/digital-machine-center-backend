package com.ency.dmc.repository;

import com.ency.dmc.model.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseRepository extends JpaRepository<License, Long> {
    List<License> findByUserId(Long userId);
    List<License> findByProductId(Long productId);
    Optional<License> findByUserIdAndProductId(Long userId, Long productId);
    Optional<License> findByLicenseKey(String licenseKey);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
