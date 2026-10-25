package com.commerceflow.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    private final UUID productId = UUID.randomUUID();

    private static ReservationResponse reservation(UUID id, UUID productId, int quantity) {
        return new ReservationResponse(id, productId, quantity, ReservationStatus.RESERVED);
    }

    @Test
    void reserveReturns201WithLocationAndBody() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationService.reserve(eq(productId), any(ReserveStockRequest.class)))
                .thenReturn(reservation(reservationId, productId, 3));

        mockMvc.perform(post("/api/v1/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity": 3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith(
                        "/api/v1/inventory/" + productId + "/reservations/" + reservationId)))
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("RESERVED"));

        ArgumentCaptor<ReserveStockRequest> captor = ArgumentCaptor.forClass(ReserveStockRequest.class);
        verify(reservationService).reserve(eq(productId), captor.capture());
        assertThat(captor.getValue().quantity()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"quantity\": 0}",
            "{\"quantity\": -2}",
            "{}",
            "{\"quantity\": \"many\"}"
    })
    void invalidQuantityReturns400WithoutCallingTheService(String body) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reservationService);
    }

    @Test
    void aMalformedProductIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/reservations", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reservationService);
    }

    @Test
    void reservingForAProductWithoutStockReturns404() throws Exception {
        when(reservationService.reserve(eq(productId), any(ReserveStockRequest.class)))
                .thenThrow(new StockNotFoundException("Stock not found: " + productId));

        mockMvc.perform(post("/api/v1/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Stock not found"));
    }

    @Test
    void reservingMoreThanIsAvailableReturns409() throws Exception {
        when(reservationService.reserve(eq(productId), any(ReserveStockRequest.class)))
                .thenThrow(new InsufficientStockException("Not enough stock for product " + productId));

        mockMvc.perform(post("/api/v1/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity": 50}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"));
    }

    @Test
    void getReturnsTheReservation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationService.get(productId, reservationId)).thenReturn(reservation(reservationId, productId, 2));

        mockMvc.perform(get("/api/v1/inventory/{productId}/reservations/{id}", productId, reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void getUnknownReservationReturns404() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationService.get(productId, reservationId))
                .thenThrow(new ReservationNotFoundException("Reservation not found: " + reservationId));

        mockMvc.perform(get("/api/v1/inventory/{productId}/reservations/{id}", productId, reservationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Reservation not found"));
    }
}
