package com.ency.dmc.dto;

import com.ency.dmc.model.*;
import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProductSearchRequest {
    private String query;
    private ContentCategory category;
    private ContentType contentType;
    private MachineType machineType;
    private String machineManufacturer;
    private String controllerManufacturer;
    private Integer numberOfAxes;
    private String contentOwner;
    private String compatibility;
    private int page = 0;
    private int size = 20;
}
