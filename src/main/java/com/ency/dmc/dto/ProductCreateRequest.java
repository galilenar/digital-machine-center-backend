package com.ency.dmc.dto;

import com.ency.dmc.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProductCreateRequest {

    @NotBlank
    private String name;

    @NotNull
    private ContentType contentType;

    @NotNull
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

    private Visibility visibility;
    private PublicationStatus publicationStatus;
    private ExperienceStatus experienceStatus;
}
