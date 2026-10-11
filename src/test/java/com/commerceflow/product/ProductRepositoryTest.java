package com.commerceflow.product;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import com.commerceflow.TestcontainersConfiguration;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void saveProductAndFetch() {
        Product product = new Product("sku1", "name1", "desc1", BigDecimal.valueOf(100));
        Product savedProduct = productRepository.save(product);

        assertThat(savedProduct.getId()).isNotNull();

        UUID id = savedProduct.getId();

        Product loadedProduct = productRepository.findById(id).orElseThrow();

        assertThat(loadedProduct).isNotNull();
        assertThat(loadedProduct.getId()).isEqualTo(id);
        assertThat(loadedProduct.getSku()).isEqualTo("sku1");
        assertThat(loadedProduct.getName()).isEqualTo("name1");
        assertThat(loadedProduct.getDescription()).isEqualTo("desc1");

    }

    @Test
    void fetchBySku() {
        Product product = new Product("sku1", "name1", "desc1", BigDecimal.valueOf(100));
        productRepository.save(product);

        Product loadedProduct = productRepository.findBySku("sku1").orElseThrow();

        assertThat(loadedProduct).isNotNull();

        loadedProduct = productRepository.findBySku("sku2").orElse(null);

        assertThat(loadedProduct).isNull();
    }

    @Test
    void saveDuplicateSku() {
        Product product = new Product("sku1", "name1", "desc1", BigDecimal.valueOf(100));
        Product savedProduct = productRepository.save(product);

        assertThat(savedProduct).isNotNull();

        Product product1 = new Product("sku1", "name1", "desc1", BigDecimal.valueOf(100));
        assertThatThrownBy(() -> productRepository.saveAndFlush(product1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}