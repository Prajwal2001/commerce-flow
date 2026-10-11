package com.commerceflow.product;

import com.commerceflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savedProductIsReadBackFromDatabase() {
        Product product = new Product("sku1", "name1", "desc1", new BigDecimal("79.9"));
        Product saved = productRepository.save(product);
        UUID id = saved.getId();
        assertThat(id).isNotNull();

        // Send the INSERT, then empty the persistence context so findById has to run a SELECT
        // instead of returning the object we just saved.
        entityManager.flush();
        entityManager.clear();

        Product loaded = productRepository.findById(id).orElseThrow();

        assertThat(loaded).isNotSameAs(saved);
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getSku()).isEqualTo("sku1");
        assertThat(loaded.getName()).isEqualTo("name1");
        assertThat(loaded.getDescription()).isEqualTo("desc1");
        // numeric(12,2) returns scale 2, so 79.9 comes back as 79.90; compare by value, not by scale.
        assertThat(loaded.getPrice()).isEqualByComparingTo("79.90");
        assertThat(loaded.getPrice().scale()).isEqualTo(2);
        // Equal only because the constructor truncates to the microsecond precision of timestamptz.
        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void descriptionIsOptional() {
        Product saved = productRepository.save(new Product("sku1", "name1", null, BigDecimal.TEN));
        entityManager.flush();
        entityManager.clear();

        assertThat(productRepository.findById(saved.getId()).orElseThrow().getDescription()).isNull();
    }

    @Test
    void findBySkuReturnsMatchingProductOrEmpty() {
        productRepository.save(new Product("sku1", "name1", "desc1", BigDecimal.valueOf(100)));

        assertThat(productRepository.findBySku("sku1")).isPresent();
        assertThat(productRepository.findBySku("sku2")).isEmpty();
    }

    @Test
    void duplicateSkuViolatesUniqueConstraint() {
        productRepository.saveAndFlush(new Product("sku1", "name1", "desc1", BigDecimal.valueOf(100)));

        Product duplicate = new Product("sku1", "other name", "other desc", BigDecimal.valueOf(200));

        assertThatThrownBy(() -> productRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nullSkuIsRejectedByDatabase() {
        Product product = new Product(null, "name1", "desc1", BigDecimal.TEN);

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nullPriceIsRejectedByDatabase() {
        Product product = new Product("sku1", "name1", "desc1", null);

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
