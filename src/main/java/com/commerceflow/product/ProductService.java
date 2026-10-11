package com.commerceflow.product;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.sku(), request.name(), request.description(), request.price());
        try {
            // saveAndFlush so the INSERT, and any constraint violation, happens inside this try block
            // instead of later at commit time, where it could not be translated here.
            return ProductResponse.from(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException e) {
            // The database constraint is the source of truth; an existsBySku check first would race
            // with a concurrent request. Do not expose e.getMessage(): it contains SQL and constraint names.
            throw new DuplicateSkuException("Product with SKU '" + request.sku() + "' already exists");
        }
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
