package com.commerceflow.product;

import com.commerceflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The generated OpenAPI document is part of the public contract: it must describe every endpoint and the
 * error responses a client has to handle. Reads the real document from the running application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class OpenApiDocumentationTest {

    @Autowired
    private RestTestClient client;

    private RestTestClient.BodyContentSpec apiDocs() {
        return client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    @Test
    void documentHasATitleAndDescription() {
        apiDocs()
                .jsonPath("$.info.title").isEqualTo("CommerceFlow API")
                .jsonPath("$.info.description").isNotEmpty()
                .jsonPath("$.info.version").isNotEmpty();
    }

    @Test
    void createDocumentsSuccessAndEveryErrorResponse() {
        apiDocs()
                .jsonPath("$.paths['/api/v1/products'].post.summary").isNotEmpty()
                .jsonPath("$.paths['/api/v1/products'].post.responses['201']").exists()
                .jsonPath("$.paths['/api/v1/products'].post.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/products'].post.responses['409']").exists();
    }

    @Test
    void getByIdDocumentsNotFound() {
        apiDocs()
                .jsonPath("$.paths['/api/v1/products/{id}'].get.summary").isNotEmpty()
                .jsonPath("$.paths['/api/v1/products/{id}'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].get.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].get.responses['404']").exists();
    }

    @Test
    void listDocumentsPagingParametersAndInvalidInput() {
        apiDocs()
                .jsonPath("$.paths['/api/v1/products'].get.summary").isNotEmpty()
                .jsonPath("$.paths['/api/v1/products'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/products'].get.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/products'].get.parameters[?(@.name == 'page')]").exists()
                .jsonPath("$.paths['/api/v1/products'].get.parameters[?(@.name == 'size')]").exists()
                .jsonPath("$.paths['/api/v1/products'].get.parameters[?(@.name == 'sort')]").exists();
    }

    @Test
    void updateDocumentsSuccessAndErrorResponses() {
        apiDocs()
                .jsonPath("$.paths['/api/v1/products/{id}'].put.summary").isNotEmpty()
                .jsonPath("$.paths['/api/v1/products/{id}'].put.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].put.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].put.responses['404']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].put.responses['409']").exists()
                .jsonPath("$.components.schemas.UpdateProductRequest.required").value(required ->
                        org.assertj.core.api.Assertions.assertThat(required.toString()).contains("version"))
                // The SKU is immutable, so the update body must not offer it.
                .jsonPath("$.components.schemas.UpdateProductRequest.properties.sku").doesNotExist()
                .jsonPath("$.components.schemas.UpdateProductRequest.properties.name").exists();
    }

    @Test
    void deleteDocumentsNoContentAndNotFound() {
        apiDocs()
                .jsonPath("$.paths['/api/v1/products/{id}'].delete.summary").isNotEmpty()
                .jsonPath("$.paths['/api/v1/products/{id}'].delete.responses['204']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].delete.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].delete.responses['404']").exists()
                .jsonPath("$.paths['/api/v1/products/{id}'].delete.responses['204'].content").doesNotExist();
    }

    @Test
    void errorResponsesAreDescribedAsProblemDetails() {
        // A client generated from this document must know the error body has a title and a detail.
        apiDocs()
                .jsonPath("$.paths['/api/v1/products'].post.responses['409'].content['application/problem+json'].schema").exists()
                .jsonPath("$.components.schemas.ProblemDetail.properties.title").exists()
                .jsonPath("$.components.schemas.ProblemDetail.properties.detail").exists();
    }

    @Test
    void swaggerUiIsServed() {
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void inventoryEndpointsAreDocumented() {
        apiDocs()
                .jsonPath("$.paths['/api/v1/inventory'].post.summary").isNotEmpty()
                .jsonPath("$.paths['/api/v1/inventory'].post.responses['201']").exists()
                .jsonPath("$.paths['/api/v1/inventory'].post.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/inventory'].post.responses['409']").exists()
                .jsonPath("$.paths['/api/v1/inventory/{productId}'].get.summary").isNotEmpty()
                .jsonPath("$.paths['/api/v1/inventory/{productId}'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/inventory/{productId}'].get.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/inventory/{productId}'].get.responses['404']").exists();
    }
}
