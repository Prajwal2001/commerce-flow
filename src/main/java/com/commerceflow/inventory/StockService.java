package com.commerceflow.inventory;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.commerceflow.common.ConstraintViolations.isViolationOf;

@Service
public class StockService {

    public static final String POSITIVE_AVAILABLE = "positive_available";
    public static final String PK_STOCK_ITEM = "pk_stock_item";
    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    public StockResponse create(CreateStockRequest request) {
        try {
            StockItem stockItem = stockRepository.saveAndFlush(new StockItem(request.productId(), request.available()));
            return StockResponse.from(stockItem);
        } catch (DataIntegrityViolationException e) {
            if (isViolationOf(e, PK_STOCK_ITEM)) {
                throw new StockAlreadyExistsException("Stock already exists for product " + request.productId());
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public StockResponse get(UUID productId) {
        return stockRepository.findById(productId)
                .map(StockResponse::from)
                .orElseThrow(() -> new StockNotFoundException("Stock not found: " + productId));
    }
}
