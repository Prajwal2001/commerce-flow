package com.commerceflow.inventory;

import com.commerceflow.TestcontainersConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class StockRepositoryTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savedStockIsReadBackWithVersionZero() {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new StockItem(productId, 12));
        entityManager.flush();
        entityManager.clear();

        StockItem loaded = stockRepository.findById(productId).orElseThrow();

        assertThat(loaded.getProductId()).isEqualTo(productId);
        assertThat(loaded.getAvailable()).isEqualTo(12);
        assertThat(loaded.getVersion()).isEqualTo(0L);
    }

    @Test
    void entityValidationRejectsNegativeStockBeforeAnySqlRuns() {
        assertThatThrownBy(() -> stockRepository.saveAndFlush(new StockItem(UUID.randomUUID(), -1)))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    void theDatabaseItselfRejectsNegativeStock() {
        // Native SQL bypasses Hibernate and its Bean Validation hook: this proves the CHECK constraint on its own.
        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("insert into stock_item (product_id, available, version) values (?1, -1, 0)")
                .setParameter(1, UUID.randomUUID())
                .executeUpdate())
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .rootCause()
                .hasMessageContaining(StockService.POSITIVE_AVAILABLE);
    }

    @Test
    void savingTheSameProductIdTwiceViolatesThePrimaryKeyInsteadOfOverwriting() {
        // The id is assigned, not generated. If Spring Data treated the second object as "not new" it would
        // merge, silently updating the first row. A new object must be INSERTed so the key rejects it.
        UUID productId = UUID.randomUUID();
        stockRepository.saveAndFlush(new StockItem(productId, 10));
        entityManager.clear();   // otherwise the persistence context rejects it before the database does

        assertThatThrownBy(() -> stockRepository.saveAndFlush(new StockItem(productId, 99)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .cause()
                .isInstanceOfSatisfying(ConstraintViolationException.class,
                        e -> assertThat(e.getConstraintName()).isEqualTo(StockService.PK_STOCK_ITEM));
    }
}
