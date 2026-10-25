package com.commerceflow.inventory;

import com.commerceflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end against real PostgreSQL. These tests only observe HTTP behaviour, so they do not care which
 * locking strategy the implementation uses: they fail for any implementation that can oversell.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ReservationApiIntegrationTest {

    @Autowired
    private RestTestClient client;

    private UUID stockOf(int available) {
        UUID productId = UUID.randomUUID();
        client.post().uri("/api/v1/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateStockRequest(productId, available))
                .exchange()
                .expectStatus().isCreated();
        return productId;
    }

    private RestTestClient.ResponseSpec reserve(UUID productId, int quantity) {
        return client.post().uri("/api/v1/inventory/{productId}/reservations", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReserveStockRequest(quantity))
                .exchange();
    }

    private int available(UUID productId) {
        return client.get().uri("/api/v1/inventory/{productId}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(StockResponse.class)
                .returnResult()
                .getResponseBody()
                .available();
    }

    @Test
    void reservingReducesTheAvailableStock() {
        UUID productId = stockOf(10);

        reserve(productId, 3)
                .expectStatus().isCreated()
                .expectBody(ReservationResponse.class)
                .value(reservation -> {
                    assertThat(reservation.id()).isNotNull();
                    assertThat(reservation.productId()).isEqualTo(productId);
                    assertThat(reservation.quantity()).isEqualTo(3);
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
                });

        assertThat(available(productId)).isEqualTo(7);
    }

    @Test
    void reservingBumpsTheStockVersionSoOptimisticWritersNoticeTheChange() {
        UUID productId = stockOf(10);
        long before = client.get().uri("/api/v1/inventory/{productId}", productId).exchange()
                .expectBody(StockResponse.class).returnResult().getResponseBody().version();

        reserve(productId, 3).expectStatus().isCreated();

        // Anyone who read the stock before this reservation must get a conflict if they write based on it.
        long after = client.get().uri("/api/v1/inventory/{productId}", productId).exchange()
                .expectBody(StockResponse.class).returnResult().getResponseBody().version();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void theReservationCanBeFetchedByItsLocation() {
        UUID productId = stockOf(10);

        String location = reserve(productId, 2)
                .expectStatus().isCreated()
                .returnResult(ReservationResponse.class)
                .getResponseHeaders().getLocation().getPath();

        client.get().uri(location).exchange()
                .expectStatus().isOk()
                .expectBody(ReservationResponse.class)
                .value(reservation -> {
                    assertThat(reservation.quantity()).isEqualTo(2);
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
                });
    }

    @Test
    void reservingExactlyTheRemainingStockSucceedsAndLeavesZero() {
        UUID productId = stockOf(10);

        reserve(productId, 10).expectStatus().isCreated();

        assertThat(available(productId)).isEqualTo(0);
    }

    @Test
    void reservingMoreThanIsAvailableReturns409AndChangesNothing() {
        UUID productId = stockOf(5);

        reserve(productId, 6).expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Insufficient stock");

        assertThat(available(productId)).isEqualTo(5);
    }

    @Test
    void reservingForAProductWithoutStockReturns404() {
        reserve(UUID.randomUUID(), 1).expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Stock not found");
    }

    @Test
    void aZeroQuantityReturns400() {
        UUID productId = stockOf(5);

        reserve(productId, 0).expectStatus().isBadRequest();

        assertThat(available(productId)).isEqualTo(5);
    }

    @Test
    void unknownReservationReturns404() {
        UUID productId = stockOf(5);

        client.get().uri("/api/v1/inventory/{productId}/reservations/{id}", productId, UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reservation not found");
    }

    @Test
    void fiftyBuyersFightingOverTenUnitsYieldExactlyTenReservations() throws Exception {
        UUID productId = stockOf(10);

        List<Integer> statuses = reserveConcurrently(productId, 50, 1);

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(status -> status == 201)).hasSize(10);
        assertThat(statuses.stream().filter(status -> status == 409)).hasSize(40);
        assertThat(available(productId)).isEqualTo(0);   // never negative, never left over
    }

    @Test
    void theLastUnitGoesToExactlyOneBuyer() throws Exception {
        UUID productId = stockOf(1);

        List<Integer> statuses = reserveConcurrently(productId, 20, 1);

        assertThat(statuses.stream().filter(status -> status == 201)).hasSize(1);
        assertThat(statuses.stream().filter(status -> status == 409)).hasSize(19);
        assertThat(available(productId)).isEqualTo(0);
    }

    @Test
    void mixedQuantitiesNeverOversell() throws Exception {
        // 15 units, 20 buyers each wanting 2: at most 7 can succeed (14 units), 1 unit must remain.
        UUID productId = stockOf(15);

        List<Integer> statuses = reserveConcurrently(productId, 20, 2);

        // Every rejected buyer must get a clean 409, not a 500 from a database constraint doing the guarding.
        assertThat(statuses).containsOnly(201, 409);
        long successes = statuses.stream().filter(status -> status == 201).count();
        assertThat(successes).isEqualTo(7);
        assertThat(available(productId)).isEqualTo(15 - 2 * 7);
    }

    /** All buyers start at the same instant so the requests really overlap. Returns one status per buyer. */
    private List<Integer> reserveConcurrently(UUID productId, int buyers, int quantity) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch allReady = new CountDownLatch(buyers);
        CountDownLatch startGun = new CountDownLatch(1);
        try {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                responses.add(pool.submit(() -> {
                    allReady.countDown();
                    startGun.await();
                    return client.post().uri("/api/v1/inventory/{productId}/reservations", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new ReserveStockRequest(quantity))
                            .exchange()
                            .returnResult()
                            .getStatus()
                            .value();
                }));
            }
            assertThat(allReady.await(15, TimeUnit.SECONDS)).isTrue();
            startGun.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
