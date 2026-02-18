package com.ency.dmc.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentCategory category;

    @Column(length = 4000)
    private String description;

    // Kit contents description (e.g., "Schema, Postprocessor")
    private String kitContents;

    // Software compatibility
    private String minSoftwareVersion;

    // Machine info
    private String machineManufacturer;
    private String machineSeries;
    private String machineModel;

    @Enumerated(EnumType.STRING)
    private MachineType machineType;

    private Integer numberOfAxes;

    // Controller info
    private String controllerManufacturer;
    private String controllerSeries;
    private String controllerModel;

    // Pricing
    private BigDecimal priceEur;

    // Ownership
    private String productOwner;
    private String authorName;

    // Trial
    private Integer trialDays;

    // Supported codes description
    @Column(length = 4000)
    private String supportedCodes;

    // Sample output code
    @Column(length = 4000)
    private String sampleOutputCode;

    // Image URL
    @Column(length = 1000)
    private String imageUrl;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus publicationStatus;

    @Enumerated(EnumType.STRING)
    private ExperienceStatus experienceStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    // Stats
    @Column(nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    // Ownership relation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Timestamps
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductComment> comments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (publicationStatus == null) {
            publicationStatus = PublicationStatus.DRAFT;
        }
        if (visibility == null) {
            visibility = Visibility.PUBLIC;
        }
        if (downloadCount == null) {
            downloadCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
