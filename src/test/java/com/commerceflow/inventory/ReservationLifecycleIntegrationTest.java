package com.commerceflow.inventory;

import com.commerceflow.TestcontainersConfiguration;
import com.commerceflow.support.Concurrently;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirm / release against real PostgreSQL. Like the reservation tests they only observe HTTP behaviour, so they
 * hold for any implementation that never returns units twice and never lets a reservation end up half way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ReservationLifecycleIntegrationTest {

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

    private UUID reserve(UUID productId, int quantity) {
        return client.post().uri("/api/v1/inventory/{productId}/reservations", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReserveStockRequest(quantity))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ReservationResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private RestTestClient.ResponseSpec act(String action, UUID productId, UUID reservationId) {
        return client.post().uri("/api/v1/inventory/{productId}/reservations/{id}/" + action, productId, reservationId)
                .exchange();
    }

    private int available(UUID productId) {
        return client.get().uri("/api/v1/inventory/{productId}", productId).exchange()
                .expectStatus().isOk()
                .expectBody(StockResponse.class)
                .returnResult()
                .getResponseBody()
                .available();
    }

    private ReservationStatus statusOf(UUID productId, UUID reservationId) {
        return client.get().uri("/api/v1/inventory/{productId}/reservations/{id}", productId, reservationId).exchange()
                .expectStatus().isOk()
                .expectBody(ReservationResponse.class)
                .returnResult()
                .getResponseBody()
                .status();
    }

    @Test
    void confirmingMarksTheReservationConfirmedAndKeepsTheUnitsDeducted() {
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 3);

        act("confirm", productId, reservationId).expectStatus().isOk()
                .expectBody(ReservationResponse.class)
                .value(reservation -> {
                    assertThat(reservation.id()).isEqualTo(reservationId);
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
                });

        assertThat(statusOf(productId, reservationId)).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(available(productId)).isEqualTo(7);
    }

    @Test
    void releasingMarksTheReservationReleasedAndReturnsTheUnits() {
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 3);
        assertThat(available(productId)).isEqualTo(7);

        act("release", productId, reservationId).expectStatus().isOk()
                .expectBody(ReservationResponse.class)
                .value(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED));

        assertThat(statusOf(productId, reservationId)).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available(productId)).isEqualTo(10);
    }

    @Test
    void releasedUnitsCanBeReservedByAnotherBuyer() {
        UUID productId = stockOf(1);
        UUID first = reserve(productId, 1);
        client.post().uri("/api/v1/inventory/{productId}/reservations", productId)
                .contentType(MediaType.APPLICATION_JSON).body(new ReserveStockRequest(1))
                .exchange().expectStatus().isEqualTo(409);

        act("release", productId, first).expectStatus().isOk();

        reserve(productId, 1);   // 201 again: the unit is back
    }

    @Test
    void theReleaseResponseMatchesWhatALaterReadReturns() {
        // A response built from an in-memory object can differ from what the database holds.
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 3);

        ReservationResponse released = act("release", productId, reservationId).expectStatus().isOk()
                .expectBody(ReservationResponse.class).returnResult().getResponseBody();

        ReservationResponse fetched = client.get()
                .uri("/api/v1/inventory/{productId}/reservations/{id}", productId, reservationId).exchange()
                .expectBody(ReservationResponse.class).returnResult().getResponseBody();
        assertThat(released).isEqualTo(fetched);
        assertThat(released.quantity()).isEqualTo(3);
    }

    @Test
    void confirmingTwiceIsIdempotent() {
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 4);

        act("confirm", productId, reservationId).expectStatus().isOk();
        act("confirm", productId, reservationId).expectStatus().isOk()
                .expectBody(ReservationResponse.class)
                .value(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED));

        assertThat(available(productId)).isEqualTo(6);
    }

    @Test
    void releasingTwiceReturnsTheUnitsOnlyOnce() {
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 4);

        act("release", productId, reservationId).expectStatus().isOk();
        act("release", productId, reservationId).expectStatus().isOk();

        assertThat(available(productId)).isEqualTo(10);   // not 14
    }

    @Test
    void aConfirmedReservationCannotBeReleased() {
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 4);
        act("confirm", productId, reservationId).expectStatus().isOk();

        act("release", productId, reservationId).expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reservation state conflict");

        assertThat(statusOf(productId, reservationId)).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(available(productId)).isEqualTo(6);
    }

    @Test
    void aReleasedReservationCannotBeConfirmed() {
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 4);
        act("release", productId, reservationId).expectStatus().isOk();

        act("confirm", productId, reservationId).expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reservation state conflict");

        assertThat(statusOf(productId, reservationId)).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available(productId)).isEqualTo(10);
    }

    @Test
    void anUnknownReservationReturns404() {
        UUID productId = stockOf(10);

        act("confirm", productId, UUID.randomUUID()).expectStatus().isNotFound();
        act("release", productId, UUID.randomUUID()).expectStatus().isNotFound();
    }

    @Test
    void aReservationCannotBeActedOnThroughAnotherProduct() {
        UUID productId = stockOf(10);
        UUID otherProduct = stockOf(10);
        UUID reservationId = reserve(productId, 4);

        act("release", otherProduct, reservationId).expectStatus().isNotFound();

        assertThat(available(productId)).isEqualTo(6);
        assertThat(available(otherProduct)).isEqualTo(10);   // nothing leaked into the other product's stock
    }

    @Test
    void tenSimultaneousReleasesOfOneReservationReturnTheUnitsExactlyOnce() throws Exception {
        UUID productId = stockOf(10);
        UUID reservationId = reserve(productId, 5);
        assertThat(available(productId)).isEqualTo(5);

        List<Integer> statuses = Concurrently.run(10, index -> act("release", productId, reservationId)
                .returnResult().getStatus().value());

        assertThat(statuses).containsOnly(200);              // repeats are idempotent, never an error
        assertThat(available(productId)).isEqualTo(10);      // 5 + 5 once, not 5 + 10 * 5
    }

    @Test
    void confirmAndReleaseRacingOnOneReservationHaveExactlyOneWinner() throws Exception {
        for (int round = 0; round < 5; round++) {
            UUID productId = stockOf(10);
            UUID reservationId = reserve(productId, 5);

            record Outcome(String action, int status) {
            }
            List<Outcome> outcomes = Concurrently.run(20, index -> {
                String action = index % 2 == 0 ? "confirm" : "release";
                return new Outcome(action, act(action, productId, reservationId).returnResult().getStatus().value());
            });

            ReservationStatus finalStatus = statusOf(productId, reservationId);
            String winningAction = finalStatus == ReservationStatus.CONFIRMED ? "confirm" : "release";
            assertThat(finalStatus).isIn(ReservationStatus.CONFIRMED, ReservationStatus.RELEASED);

            // The winner's action (and its repeats) succeed; the opposite action must be refused, never half applied.
            assertThat(outcomes).allSatisfy(outcome ->
                    assertThat(outcome.status()).isEqualTo(outcome.action().equals(winningAction) ? 200 : 409));
            assertThat(available(productId)).isEqualTo(finalStatus == ReservationStatus.CONFIRMED ? 5 : 10);
        }
    }
}
