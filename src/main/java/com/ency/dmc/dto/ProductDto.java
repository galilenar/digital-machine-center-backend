package com.ency.dmc.dto;

import com.ency.dmc.model.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private ContentType contentType;
    private ContentCategory category;
    private String description;
    private String kitContents;
    private String minSoftwareVersion;

    private String machineManufacturer;
    private String machineSeries;
    private String machineModel;
    private MachineType machineType;
    private Integer numberOfAxes;

    private String controllerManufacturer;
    private String controllerSeries;
    private String controllerModel;

    private BigDecimal priceEur;
    private String productOwner;
    private String authorName;
    private Integer trialDays;

    private String supportedCodes;
    private String sampleOutputCode;
    private String imageUrl;

    private PublicationStatus publicationStatus;
    private ExperienceStatus experienceStatus;
    private Visibility visibility;

    private Integer downloadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    private Long ownerId;
    private String ownerUsername;
}
