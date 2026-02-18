package com.ency.dmc.repository;

import com.ency.dmc.model.ProductComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCommentRepository extends JpaRepository<ProductComment, Long> {
    List<ProductComment> findByProductIdOrderByCreatedAtDesc(Long productId);
}
