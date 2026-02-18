package com.ency.dmc.service;

import com.ency.dmc.dto.*;
import com.ency.dmc.model.*;
import com.ency.dmc.repository.ProductRepository;
import com.ency.dmc.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public Page<ProductDto> search(ProductSearchRequest request) {
        String sortField = request.getSortBy() != null ? request.getSortBy() : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(request.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by(direction, sortField));

        Specification<Product> spec = buildSpecification(request);
        Page<Product> products = productRepository.findAll(spec, pageable);

        return products.map(this::toDto);
    }

    public List<ProductDto> findAll() {
        return productRepository.findAll(Sort.by("createdAt").descending())
                .stream().map(this::toDto).toList();
    }

    public List<ProductDto> findByOwnerId(Long ownerId) {
        return productRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(this::toDto).toList();
    }

    public ProductDto findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        return toDto(product);
    }

    @Transactional
    public ProductDto create(ProductCreateRequest request) {
        return create(request, null);
    }

    @Transactional
    public ProductDto create(ProductCreateRequest request, String ownerUsername) {
        Product product = Product.builder()
                .name(request.getName())
                .contentType(request.getContentType())
                .category(request.getCategory())
                .description(request.getDescription())
                .kitContents(request.getKitContents())
                .minSoftwareVersion(request.getMinSoftwareVersion())
                .machineManufacturer(request.getMachineManufacturer())
                .machineSeries(request.getMachineSeries())
                .machineModel(request.getMachineModel())
                .machineType(request.getMachineType())
                .numberOfAxes(request.getNumberOfAxes())
                .controllerManufacturer(request.getControllerManufacturer())
                .controllerSeries(request.getControllerSeries())
                .controllerModel(request.getControllerModel())
                .priceEur(request.getPriceEur())
                .productOwner(request.getProductOwner())
                .authorName(request.getAuthorName())
                .trialDays(request.getTrialDays())
                .supportedCodes(request.getSupportedCodes())
                .sampleOutputCode(request.getSampleOutputCode())
                .imageUrl(request.getImageUrl())
                .publicationStatus(request.getPublicationStatus() != null
                        ? request.getPublicationStatus() : PublicationStatus.DRAFT)
                .experienceStatus(request.getExperienceStatus() != null
                        ? request.getExperienceStatus() : ExperienceStatus.NOT_TESTED)
                .visibility(request.getVisibility() != null ? request.getVisibility() : Visibility.PUBLIC)
                .downloadCount(0)
                .build();

        if (ownerUsername != null) {
            userRepository.findByUsername(ownerUsername).ifPresent(product::setOwner);
        }

        product = productRepository.save(product);
        return toDto(product);
    }

    @Transactional
    public ProductDto update(Long id, ProductCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        product.setName(request.getName());
        product.setContentType(request.getContentType());
        product.setCategory(request.getCategory());
        product.setDescription(request.getDescription());
        product.setKitContents(request.getKitContents());
        product.setMinSoftwareVersion(request.getMinSoftwareVersion());
        product.setMachineManufacturer(request.getMachineManufacturer());
        product.setMachineSeries(request.getMachineSeries());
        product.setMachineModel(request.getMachineModel());
        product.setMachineType(request.getMachineType());
        product.setNumberOfAxes(request.getNumberOfAxes());
        product.setControllerManufacturer(request.getControllerManufacturer());
        product.setControllerSeries(request.getControllerSeries());
        product.setControllerModel(request.getControllerModel());
        product.setPriceEur(request.getPriceEur());
        product.setProductOwner(request.getProductOwner());
        product.setAuthorName(request.getAuthorName());
        product.setTrialDays(request.getTrialDays());
        product.setSupportedCodes(request.getSupportedCodes());
        product.setSampleOutputCode(request.getSampleOutputCode());
        product.setImageUrl(request.getImageUrl());
        if (request.getVisibility() != null) {
            product.setVisibility(request.getVisibility());
        }

        product = productRepository.save(product);
        return toDto(product);
    }

    @Transactional
    public ProductDto updateStatus(Long id, PublicationStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        product.setPublicationStatus(status);
        if (status == PublicationStatus.PUBLISHED) {
            product.setPublishedAt(java.time.LocalDateTime.now());
        }
        product = productRepository.save(product);
        return toDto(product);
    }

    @Transactional
    public void incrementDownloadCount(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        product.setDownloadCount(product.getDownloadCount() + 1);
        productRepository.save(product);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    public FilterOptionsDto getFilterOptions() {
        return FilterOptionsDto.builder()
                .machineManufacturers(productRepository.findDistinctMachineManufacturers())
                .controllerManufacturers(productRepository.findDistinctControllerManufacturers())
                .contentOwners(productRepository.findDistinctProductOwners())
                .numberOfAxes(productRepository.findDistinctNumberOfAxes())
                .contentTypes(Arrays.asList(ContentType.values()))
                .machineTypes(Arrays.asList(MachineType.values()))
                .categories(Arrays.asList(ContentCategory.values()))
                .build();
    }

    private Specification<Product> buildSpecification(ProductSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only return PUBLISHED products for the public search
            predicates.add(cb.equal(root.get("publicationStatus"), PublicationStatus.PUBLISHED));

            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                String pattern = "%" + request.getQuery().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("machineManufacturer")), pattern),
                        cb.like(cb.lower(root.get("machineModel")), pattern),
                        cb.like(cb.lower(root.get("productOwner")), pattern)
                ));
            }

            if (request.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), request.getCategory()));
            }
            if (request.getContentType() != null) {
                predicates.add(cb.equal(root.get("contentType"), request.getContentType()));
            }
            if (request.getMachineType() != null) {
                predicates.add(cb.equal(root.get("machineType"), request.getMachineType()));
            }
            if (request.getMachineManufacturer() != null && !request.getMachineManufacturer().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("machineManufacturer")),
                        request.getMachineManufacturer().toLowerCase()));
            }
            if (request.getControllerManufacturer() != null && !request.getControllerManufacturer().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("controllerManufacturer")),
                        request.getControllerManufacturer().toLowerCase()));
            }
            if (request.getNumberOfAxes() != null) {
                predicates.add(cb.equal(root.get("numberOfAxes"), request.getNumberOfAxes()));
            }
            if (request.getContentOwner() != null && !request.getContentOwner().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("productOwner")),
                        request.getContentOwner().toLowerCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ProductDto toDto(Product p) {
        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .contentType(p.getContentType())
                .category(p.getCategory())
                .description(p.getDescription())
                .kitContents(p.getKitContents())
                .minSoftwareVersion(p.getMinSoftwareVersion())
                .machineManufacturer(p.getMachineManufacturer())
                .machineSeries(p.getMachineSeries())
                .machineModel(p.getMachineModel())
                .machineType(p.getMachineType())
                .numberOfAxes(p.getNumberOfAxes())
                .controllerManufacturer(p.getControllerManufacturer())
                .controllerSeries(p.getControllerSeries())
                .controllerModel(p.getControllerModel())
                .priceEur(p.getPriceEur())
                .productOwner(p.getProductOwner())
                .authorName(p.getAuthorName())
                .trialDays(p.getTrialDays())
                .supportedCodes(p.getSupportedCodes())
                .sampleOutputCode(p.getSampleOutputCode())
                .imageUrl(p.getImageUrl())
                .publicationStatus(p.getPublicationStatus())
                .experienceStatus(p.getExperienceStatus())
                .visibility(p.getVisibility())
                .downloadCount(p.getDownloadCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .publishedAt(p.getPublishedAt())
                .ownerId(p.getOwner() != null ? p.getOwner().getId() : null)
                .ownerUsername(p.getOwner() != null ? p.getOwner().getUsername() : null)
                .build();
    }
}
