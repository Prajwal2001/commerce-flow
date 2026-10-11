package com.commerceflow.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
        assertThat(productResponse.price()).isEqualTo(BigDecimal.ONE);
        assertThat(productResponse.createdAt()).isNotNull();
    }

    @Test
    void duplicateSku() {
        // Shaped like a real driver message: the service must not pass any of this text to the client.
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \"uk_internal_name\"]"));

        assertThatThrownBy(() -> productService.create(new CreateProductRequest("sku1", "name1", "desc1", BigDecimal.ONE)))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("sku1")
                .hasMessageNotContaining("duplicate key")
                .hasMessageNotContaining("uk_internal_name");
    }

    @Test
    void missingProductId() {
        when(productRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(UUID.randomUUID()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void productList() {
        when(productRepository.findAll()).thenReturn(List.of(
                new Product("sku1", "name1", "desc1", BigDecimal.ONE),
                new Product("sku2", "name2", "desc2", BigDecimal.TWO)
        ));

        List<ProductResponse> productResponses = productService.list();

        assertThat(productResponses).isNotNull();
        assertThat(productResponses)
                .extracting(ProductResponse::sku)
                .containsExactly("sku1", "sku2");
    }

}