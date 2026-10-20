package com.commerceflow.product;

import com.commerceflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.StatusAssertions;

import java.math.BigDecimal;
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
 * End-to-end: real HTTP request -> controller -> service -> Hibernate -> PostgreSQL (Testcontainers).
 * <p>
 * Unlike @DataJpaTest, @SpringBootTest does NOT roll back between tests, so every test must use
 * its own SKU or it will collide with rows left behind by another test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ProductApiIntegrationTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductRepository productRepository;

    private static String uniqueSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static CreateProductRequest request(String sku) {
        return new CreateProductRequest(sku, "Keyboard", "Mechanical", new BigDecimal("79.99"));
    }

    @Test
    void createdProductCanBeFetchedByItsLocation() {
        String sku = uniqueSku();

        RestTestClient.ResponseSpec createResponse = client.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request(sku))
                .exchange();

        ProductResponse created = createResponse
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.sku()).isEqualTo(sku);

        client.get()
                .uri("/api/v1/products/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(fetched -> {
                    assertThat(fetched.id()).isEqualTo(created.id());
                    assertThat(fetched.sku()).isEqualTo(sku);
                    assertThat(fetched.price()).isEqualByComparingTo("79.99");
                    assertThat(fetched.createdAt()).isEqualTo(created.createdAt());
                });
    }

    @Test
    void duplicateSkuReturns409FromTheDatabaseConstraint() {
        String sku = uniqueSku();

        client.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request(sku))
                .exchange()
                .expectStatus().isCreated();

        client.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateProductRequest(sku, "Different name", null, BigDecimal.TEN))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Duplicate SKU")
                .jsonPath("$.detail").isEqualTo("Product with SKU '" + sku + "' already exists");
    }

    @Test
    void unknownProductIdReturns404() {
        UUID id = UUID.randomUUID();

        client.get().uri("/api/v1/products/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Product not found");
    }

    @Test
    void invalidBodyReturns400ListingTheFailingFields() {
        client.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateProductRequest("", "Keyboard", null, new BigDecimal("-1")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation failed")
                .jsonPath("$.errors[?(@.field == 'sku')]").exists()
                .jsonPath("$.errors[?(@.field == 'price')]").exists()
                .jsonPath("$.errors[?(@.field == 'name')]").doesNotExist();
    }

    @Test
    void priceThatIsNotANumberReturns400() {
        // A raw string: a body the CreateProductRequest record could never produce.
        String body = """
                {"sku": "%s", "name": "Keyboard", "price": "abc"}
                """.formatted(uniqueSku());

        client.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void concurrentCreatesWithTheSameSkuYieldExactlyOneProduct() throws Exception {
        int attempts = 10;
        String sku = uniqueSku();
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch allReady = new CountDownLatch(attempts);
        CountDownLatch startGun = new CountDownLatch(1);

        try {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                responses.add(pool.submit(() -> {
                    allReady.countDown();
                    startGun.await();   // every thread waits here so the requests really overlap
                    return client.post().uri("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request(sku))
                            .exchange()
                            .returnResult()
                            .getStatus()
                            .value();
                }));
            }
            assertThat(allReady.await(10, TimeUnit.SECONDS)).isTrue();
            startGun.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsOnly(201, 409);
            assertThat(statuses.stream().filter(status -> status == 201)).hasSize(1);
            assertThat(statuses.stream().filter(status -> status == 409)).hasSize(attempts - 1);
        } finally {
            pool.shutdownNow();
        }

        // The database, not just the HTTP responses, must hold exactly one row for this SKU.
        assertThat(productRepository.findAll()).filteredOn(product -> sku.equals(product.getSku())).hasSize(1);
    }

    @Test
    void listIsPagedNewestFirstByDefault() {
        String older = uniqueSku();
        String newer = uniqueSku();
        client.post().uri("/api/v1/products").contentType(MediaType.APPLICATION_JSON).body(request(older)).exchange().expectStatus().isCreated();
        client.post().uri("/api/v1/products").contentType(MediaType.APPLICATION_JSON).body(request(newer)).exchange().expectStatus().isCreated();

        client.get().uri("/api/v1/products?size=2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].sku").isEqualTo(newer)
                .jsonPath("$.content[1].sku").isEqualTo(older)
                .jsonPath("$.content[2]").doesNotExist()
                .jsonPath("$.size").isEqualTo(2)
                .jsonPath("$.page").isEqualTo(0);
    }

    @Test
    void unknownSortPropertyReturns400NotAServerError() {
        client.get().uri("/api/v1/products?sort=password,asc")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Invalid sort")
                .jsonPath("$.detail").value(detail -> assertThat(detail.toString()).contains("password").contains("createdAt"));
    }

    @Test
    void priceIsReturnedWithTwoDecimalPlacesByCreateAndGetAlike() {
        // 79.9 has scale 1; the database column is numeric(12,2). Both endpoints must agree.
        ProductResponse created = client.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateProductRequest(uniqueSku(), "Keyboard", null, new BigDecimal("79.9")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.price().scale()).isEqualTo(2);

        client.get().uri("/api/v1/products/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(fetched -> assertThat(fetched.price()).isEqualTo(created.price()));
    }
}
