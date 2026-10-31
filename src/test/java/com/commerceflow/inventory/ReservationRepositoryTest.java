package com.commerceflow.inventory;

import com.commerceflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarded release query, tested directly. Through HTTP a write on the wrong product is invisible because the
 * request fails with 404 and the transaction rolls the write back; only a test on the query itself can see it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Reservation saved(UUID productId) {
        stockRepository.save(new StockItem(productId, 10));
        Reservation reservation = reservationRepository.save(Reservation.reserve(productId, 3));
        entityManager.flush();
        entityManager.clear();
        return reservation;
    }

    private Reservation reload(UUID id) {
        entityManager.clear();
        return reservationRepository.findById(id).orElseThrow();
    }

    @Test
    void releasingARESERVEDReservationUpdatesOneRowAndBumpsTheVersion() {
        UUID productId = UUID.randomUUID();
        Reservation reservation = saved(productId);

        int updated = reservationRepository.updateStatusToRelease(reservation.getId(), productId);

        assertThat(updated).isEqualTo(1);
        Reservation after = reload(reservation.getId());
        assertThat(after.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(after.getVersion()).isEqualTo(reservation.getVersion() + 1);
    }

    @Test
    void theQueryDoesNotTouchAReservationOfAnotherProduct() {
        Reservation reservation = saved(UUID.randomUUID());

        int updated = reservationRepository.updateStatusToRelease(reservation.getId(), UUID.randomUUID());

        assertThat(updated).isZero();
        assertThat(reload(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void theQueryDoesNotTouchAConfirmedReservation() {
        UUID productId = UUID.randomUUID();
        stockRepository.save(new StockItem(productId, 10));
        Reservation reservation = reservationRepository.save(Reservation.reserve(productId, 3));
        reservation.confirm();
        entityManager.flush();
        entityManager.clear();

        int updated = reservationRepository.updateStatusToRelease(reservation.getId(), productId);

        assertThat(updated).isZero();
        assertThat(reload(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void releasingAnAlreadyReleasedReservationUpdatesNothing() {
        UUID productId = UUID.randomUUID();
        Reservation reservation = saved(productId);
        reservationRepository.updateStatusToRelease(reservation.getId(), productId);

        int second = reservationRepository.updateStatusToRelease(reservation.getId(), productId);

        assertThat(second).isZero();
        assertThat(reload(reservation.getId()).getVersion()).isEqualTo(reservation.getVersion() + 1);
    }
}
