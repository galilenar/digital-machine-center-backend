package com.ency.dmc.controller;

import com.ency.dmc.dto.*;
import com.ency.dmc.model.*;
import com.ency.dmc.repository.UserRepository;
import com.ency.dmc.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<ProductDto>> getAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    @GetMapping("/my")
    public ResponseEntity<List<ProductDto>> getMyProducts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return userRepository.findByUsername(username)
                .map(user -> ResponseEntity.ok(productService.findByOwnerId(user.getId())))
                .orElse(ResponseEntity.ok(Collections.emptyList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<Page<ProductDto>> search(@RequestBody ProductSearchRequest request) {
        return ResponseEntity.ok(productService.search(request));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(
            @Valid @RequestBody ProductCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String username = extractUsername(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.create(request, username));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> update(@PathVariable Long id,
                                             @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductDto> updateStatus(@PathVariable Long id,
                                                   @RequestParam PublicationStatus status) {
        return ResponseEntity.ok(productService.updateStatus(id, status));
    }

    @PostMapping("/{id}/download")
    public ResponseEntity<Void> recordDownload(@PathVariable Long id) {
        productService.incrementDownloadCount(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filters")
    public ResponseEntity<FilterOptionsDto> getFilterOptions() {
        return ResponseEntity.ok(productService.getFilterOptions());
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        try {
            String token = authHeader.replace("Bearer ", "").replace("Basic ", "");
            String decoded = new String(Base64.getDecoder().decode(token));
            if (decoded.contains(":")) {
                return decoded.split(":")[0];
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
