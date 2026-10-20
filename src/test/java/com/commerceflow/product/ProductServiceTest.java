package com.commerceflow.product;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void create() {
        UUID id = UUID.randomUUID();
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product product = inv.getArgument(0);
            ReflectionTestUtils.setField(product, "id", id);
            return product;
        });

        ProductResponse productResponse = productService.create(new CreateProductRequest("sku1", "name1", "desc1", BigDecimal.ONE));

        assertThat(productResponse).isNotNull();
        assertThat(productResponse.id()).isEqualTo(id);
        assertThat(productResponse.sku()).isEqualTo("sku1");
        assertThat(productResponse.name()).isEqualTo("name1");
        assertThat(productResponse.description()).isEqualTo("desc1");
        assertThat(productResponse.price()).isEqualTo(new BigDecimal("1.00"));
        assertThat(productResponse.createdAt()).isNotNull();
    }

    @Test
    void duplicateSkuIsRecognisedByTheNamedConstraint() {
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(violationOf("uq_products_sku"));

        assertThatThrownBy(() -> productService.create(new CreateProductRequest("sku1", "name1", "desc1", BigDecimal.ONE)))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("sku1")
                // The client must not see driver text or constraint names.
                .hasMessageNotContaining("duplicate key")
                .hasMessageNotContaining("uq_products_sku");
    }

    @Test
    void otherIntegrityViolationsAreNotReportedAsDuplicateSku() {
        // A different constraint failing is a bug or a bad deployment, not a client conflict: it must stay a 500.
        DataIntegrityViolationException other = violationOf("ck_products_price_positive");
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(other);

        assertThatThrownBy(() -> productService.create(new CreateProductRequest("sku1", "name1", "desc1", BigDecimal.ONE)))
                .isSameAs(other);
    }

    /** Shaped like what Spring really throws: a DataIntegrityViolationException wrapping Hibernate's, which carries the constraint name. */
    private static DataIntegrityViolationException violationOf(String constraintName) {
        String message = "could not execute statement [ERROR: duplicate key value violates unique constraint \"" + constraintName + "\"]";
        return new DataIntegrityViolationException(message,
                new ConstraintViolationException(message, new SQLException("violation"), constraintName));
    }

    @Test
    void missingProductId() {
        when(productRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(UUID.randomUUID()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void listMapsTheRepositoryPageToAResponse() {
        Pageable pageable = PageRequest.of(1, 2, Sort.by("price"));
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(
                new Product("sku3", "name3", "desc3", BigDecimal.ONE),
                new Product("sku4", "name4", "desc4", BigDecimal.TWO)
        ), pageable, 7));

        PageResponse<ProductResponse> response = productService.list(pageable);

        assertThat(response.content()).extracting(ProductResponse::sku).containsExactly("sku3", "sku4");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(7);
        assertThat(response.totalPages()).isEqualTo(4);   // ceil(7 / 2)
    }

    @Test
    void updateChangesTheEditableFieldsAndKeepsIdentity() {
        UUID id = UUID.randomUUID();
        Product existing = new Product("sku1", "old name", "old desc", BigDecimal.TEN);
        ReflectionTestUtils.setField(existing, "id", id);
        ReflectionTestUtils.setField(existing, "version", 3L);
        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        // For a managed entity the real saveAndFlush returns the same instance.
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.update(id, new UpdateProductRequest("new name", null, new BigDecimal("25.5"), 3L));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.sku()).isEqualTo("sku1");
        assertThat(response.name()).isEqualTo("new name");
        assertThat(response.description()).isNull();
        // Same invariant as create: two decimals, so a PUT response matches what a later GET returns.
        assertThat(response.price()).isEqualTo(new BigDecimal("25.50"));
        assertThat(response.createdAt()).isEqualTo(existing.getCreatedAt());
    }

    @Test
    void updateOfAMissingProductThrowsNotFound() {
        when(productRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(UUID.randomUUID(), new UpdateProductRequest("n", null, BigDecimal.ONE, 0L)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateWithAStaleVersionIsRejectedAndChangesNothing() {
        UUID id = UUID.randomUUID();
        Product existing = new Product("sku1", "old name", "old desc", BigDecimal.TEN);
        ReflectionTestUtils.setField(existing, "id", id);
        ReflectionTestUtils.setField(existing, "version", 3L);
        when(productRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> productService.update(id, new UpdateProductRequest("new name", null, BigDecimal.ONE, 2L)))
                .isInstanceOf(ProductVersionConflictException.class);

        assertThat(existing.getName()).isEqualTo("old name");
        assertThat(existing.getPrice()).isEqualByComparingTo("10");
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }
}
