package com.ency.dmc.repository;

import com.ency.dmc.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findByPublicationStatus(PublicationStatus status);

    List<Product> findByCategory(ContentCategory category);

    List<Product> findByContentType(ContentType contentType);

    List<Product> findByMachineManufacturerIgnoreCase(String manufacturer);

    @Query("SELECT DISTINCT p.machineManufacturer FROM Product p WHERE p.machineManufacturer IS NOT NULL ORDER BY p.machineManufacturer")
    List<String> findDistinctMachineManufacturers();

    @Query("SELECT DISTINCT p.controllerManufacturer FROM Product p WHERE p.controllerManufacturer IS NOT NULL ORDER BY p.controllerManufacturer")
    List<String> findDistinctControllerManufacturers();

    @Query("SELECT DISTINCT p.productOwner FROM Product p WHERE p.productOwner IS NOT NULL ORDER BY p.productOwner")
    List<String> findDistinctProductOwners();

    @Query("SELECT DISTINCT p.numberOfAxes FROM Product p WHERE p.numberOfAxes IS NOT NULL ORDER BY p.numberOfAxes")
    List<Integer> findDistinctNumberOfAxes();

    List<Product> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
