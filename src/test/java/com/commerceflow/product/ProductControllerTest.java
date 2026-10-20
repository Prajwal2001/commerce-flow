package com.commerceflow.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final String VALID_BODY = """
            {"sku": "KB-001", "name": "Keyboard", "description": "Mechanical", "price": 79.99}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private ProductResponse sampleResponse(UUID id) {
        return new ProductResponse(id, "KB-001", "Keyboard", "Mechanical", new BigDecimal("79.99"), Instant.parse("2026-10-12T10:00:00Z"), 0L);
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.create(any(CreateProductRequest.class))).thenReturn(sampleResponse(id));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.sku").value("KB-001"))
                .andExpect(jsonPath("$.price").value(79.99));
    }

    @Test
    void createWithInvalidFieldsReturns400ListingEachField() throws Exception {
        String body = """
                {"sku": "", "name": "Keyboard", "price": -5}
                """;

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'sku')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").doesNotExist());

        verifyNoInteractions(productService);
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{\"sku\": "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void createWithDuplicateSkuReturns409() throws Exception {
        when(productService.create(any(CreateProductRequest.class)))
                .thenThrow(new DuplicateSkuException("Product with SKU 'KB-001' already exists"));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate SKU"))
                .andExpect(jsonPath("$.detail").value("Product with SKU 'KB-001' already exists"));
    }

    @Test
    void getReturnsProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.get(id)).thenReturn(sampleResponse(id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Keyboard"));
    }

    @Test
    void getMissingProductReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.get(id)).thenThrow(new ProductNotFoundException("Product not found: " + id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    void getWithMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void listReturnsThePageEnvelope() throws Exception {
        PageResponse<ProductResponse> page = new PageResponse<>(
                List.of(sampleResponse(UUID.randomUUID()), sampleResponse(UUID.randomUUID())), 0, 20, 2, 1);
        when(productService.list(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("KB-001"))
                .andExpect(jsonPath("$.content[1]").exists())
                .andExpect(jsonPath("$.content[2]").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listWithoutParametersUsesTheDefaults() throws Exception {
        when(productService.list(any(Pageable.class))).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void listPassesPageSizeAndSortToTheService() throws Exception {
        when(productService.list(any(Pageable.class))).thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/v1/products").param("page", "2").param("size", "5").param("sort", "price,asc"))
                .andExpect(status().isOk());

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "price"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "size=101",              // above the maximum page size
            "size=0",
            "page=-1",
            "sort=password,asc",     // not a sortable field
            "sort=price,sideways"    // not a direction
    })
    void invalidPagingParametersReturn400WithoutCallingTheService(String query) throws Exception {
        mockMvc.perform(get("/api/v1/products?" + query))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    private static final String VALID_UPDATE = """
            {"name": "Keyboard v2", "description": "Hot-swappable", "price": 89.90, "version": 4}
            """;

    @Test
    void updateReturns200WithTheUpdatedProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.update(eq(id), any(UpdateProductRequest.class))).thenReturn(sampleResponse(id));

        mockMvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        ArgumentCaptor<UpdateProductRequest> captor = ArgumentCaptor.forClass(UpdateProductRequest.class);
        verify(productService).update(eq(id), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Keyboard v2");
        assertThat(captor.getValue().price()).isEqualByComparingTo("89.90");
        assertThat(captor.getValue().version()).isEqualTo(4L);
    }

    @Test
    void updateWithoutAVersionReturns400() throws Exception {
        // Without the version the server cannot tell a stale write from a fresh one.
        mockMvc.perform(put("/api/v1/products/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Keyboard v2", "price": 89.90}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'version')]").exists());

        verifyNoInteractions(productService);
    }

    @Test
    void updateWithAStaleVersionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.update(eq(id), any(UpdateProductRequest.class)))
                .thenThrow(new ProductVersionConflictException("Product " + id + " was changed by someone else; reload and retry"));

        mockMvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Version conflict"))
                // Hibernate's own message names the entity class and id; the client must not see internals.
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.commerceflow"))));
    }

    @Test
    void aLockingFailureRaisedAtCommitTimeAlsoReturns409() throws Exception {
        // The race case: both writers passed the version check, the database refused the loser's UPDATE.
        UUID id = UUID.randomUUID();
        when(productService.update(eq(id), any(UpdateProductRequest.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Product.class, id));

        mockMvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Version conflict"))
                // Hibernate's own message names the entity class and id; the client must not see internals.
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.commerceflow"))));
    }

    @Test
    void updateWithInvalidFieldsReturns400ListingEachField() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": -1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists());

        verifyNoInteractions(productService);
    }

    @Test
    void updateOfAnUnknownProductReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.update(eq(id), any(UpdateProductRequest.class))).thenThrow(new ProductNotFoundException("Product not found: " + id));

        mockMvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    void updateWithAMalformedIdReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", "not-a-uuid").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/products/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(productService).delete(id);
    }

    @Test
    void deleteOfAnUnknownProductReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProductNotFoundException("Product not found: " + id)).when(productService).delete(id);

        mockMvc.perform(delete("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    void deleteWithAMalformedIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).list(captor.capture());
        return captor.getValue();
    }
}
