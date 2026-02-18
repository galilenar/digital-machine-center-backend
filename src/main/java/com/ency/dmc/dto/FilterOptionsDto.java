package com.ency.dmc.dto;

import com.ency.dmc.model.*;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FilterOptionsDto {
    private List<String> machineManufacturers;
    private List<String> controllerManufacturers;
    private List<String> contentOwners;
    private List<Integer> numberOfAxes;
    private List<ContentType> contentTypes;
    private List<MachineType> machineTypes;
    private List<ContentCategory> categories;
}
