package com.commerceflow.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockService stockService;

    private static String body(UUID productId, String available) {
        return """
                {"productId": "%s", "available": %s}
                """.formatted(productId, available);
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        UUID productId = UUID.randomUUID();
        when(stockService.create(any(CreateStockRequest.class))).thenReturn(new StockResponse(productId, 10, 0L));

        mockMvc.perform(post("/api/v1/inventory").contentType(MediaType.APPLICATION_JSON).content(body(productId, "10")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/inventory/" + productId)))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.available").value(10))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createWithAnInvalidBodyReturns400ListingEachField() throws Exception {
        mockMvc.perform(post("/api/v1/inventory").contentType(MediaType.APPLICATION_JSON).content("""
                        {"available": -1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'productId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'available')]").exists());

        verifyNoInteractions(stockService);
    }

    @Test
    void createWithAMalformedProductIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/inventory").contentType(MediaType.APPLICATION_JSON).content("""
                        {"productId": "not-a-uuid", "available": 5}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stockService);
    }

    @Test
    void createWhenStockAlreadyExistsReturns409() throws Exception {
        UUID productId = UUID.randomUUID();
        when(stockService.create(any(CreateStockRequest.class)))
                .thenThrow(new StockAlreadyExistsException("Stock already exists for product " + productId));

        mockMvc.perform(post("/api/v1/inventory").contentType(MediaType.APPLICATION_JSON).content(body(productId, "10")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Stock already exists"));
    }

    @Test
    void getReturnsTheStock() throws Exception {
        UUID productId = UUID.randomUUID();
        when(stockService.get(productId)).thenReturn(new StockResponse(productId, 7, 3L));

        mockMvc.perform(get("/api/v1/inventory/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(7))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void getUnknownStockReturns404() throws Exception {
        UUID productId = UUID.randomUUID();
        when(stockService.get(productId)).thenThrow(new StockNotFoundException("Stock not found for product " + productId));

        mockMvc.perform(get("/api/v1/inventory/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Stock not found"));
    }

    @Test
    void getWithAMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/{productId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stockService);
    }
}
