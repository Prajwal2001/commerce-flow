package com.commerceflow.inventory;

import com.commerceflow.TestcontainersConfiguration;
import com.commerceflow.support.Concurrently;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** End to end against real PostgreSQL. Each test uses its own productId because nothing is rolled back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class StockApiIntegrationTest {

    @Autowired
    private RestTestClient client;

    private RestTestClient.ResponseSpec create(UUID productId, int available) {
        return client.post().uri("/api/v1/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateStockRequest(productId, available))
                .exchange();
    }

    @Test
    void createdStockCanBeFetchedByItsLocation() {
        UUID productId = UUID.randomUUID();

        String location = create(productId, 10)
                .expectStatus().isCreated()
                .returnResult(StockResponse.class)
                .getResponseHeaders().getLocation().getPath();

        assertThat(location).isEqualTo("/api/v1/inventory/" + productId);
        client.get().uri(location).exchange()
                .expectStatus().isOk()
                .expectBody(StockResponse.class)
                .value(stock -> {
                    assertThat(stock.productId()).isEqualTo(productId);
                    assertThat(stock.available()).isEqualTo(10);
                    assertThat(stock.version()).isEqualTo(0L);
                });
    }

    @Test
    void zeroStockIsAllowed() {
        create(UUID.randomUUID(), 0).expectStatus().isCreated();
    }

    @Test
    void stockCanBeCreatedForAProductTheCatalogDoesNotKnow() {
        // The modules own their data separately: inventory does not call or join the catalog.
        UUID neverCreatedInTheCatalog = UUID.randomUUID();

        create(neverCreatedInTheCatalog, 5).expectStatus().isCreated();
    }

    @Test
    void creatingStockTwiceForTheSameProductReturns409AndKeepsTheFirstQuantity() {
        UUID productId = UUID.randomUUID();
        create(productId, 10).expectStatus().isCreated();

        create(productId, 99).expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Stock already exists");

        client.get().uri("/api/v1/inventory/{id}", productId).exchange()
                .expectBody(StockResponse.class)
                .value(stock -> assertThat(stock.available()).isEqualTo(10));
    }

    @Test
    void negativeStockReturns400() {
        client.post().uri("/api/v1/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"productId": "%s", "available": -1}
                        """.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[?(@.field == 'available')]").exists();
    }

    @Test
    void unknownStockReturns404() {
        client.get().uri("/api/v1/inventory/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Stock not found");
    }

    @Test
    void concurrentCreatesForTheSameProductYieldExactlyOneStockRow() throws Exception {
        UUID productId = UUID.randomUUID();

        List<Integer> statuses = Concurrently.run(10, index -> client.post().uri("/api/v1/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateStockRequest(productId, index + 1))
                .exchange()
                .returnResult()
                .getStatus()
                .value());

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(status -> status == 201)).hasSize(1);
    }
}
