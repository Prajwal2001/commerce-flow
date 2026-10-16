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
import java.util.UUID;

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
}
