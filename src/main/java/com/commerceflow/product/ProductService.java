package com.commerceflow.product;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.commerceflow.common.ConstraintViolations.isViolationOf;

@Service
public class ProductService {

    private static final String SKU_CONSTRAINT = "uq_products_sku";

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
            if (isViolationOf(e, SKU_CONSTRAINT)) {
                throw new DuplicateSkuException("Product with SKU '" + request.sku() + "' already exists");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Pageable pageable) {

        Page<Product> page = productRepository.findAll(pageable);
        List<ProductResponse> productResponseList = page.stream().map(ProductResponse::from).toList();

        return new PageResponse<>(productResponseList, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));

        if (product.getVersion() != request.version()) {
            throw new ProductVersionConflictException("Product " + id + " is at version " + product.getVersion() + " but the request is based on version " + request.version() + "; reload it and retry");
        }

        product.updateData(request.name(), request.description(), request.price());

        Product updatedProduct = productRepository.saveAndFlush(product);

        return ProductResponse.from(updatedProduct);
    }

    @Transactional
    public void delete(UUID id) {
        // No find-then-delete: with concurrent deletes every caller would find the row, and the losers would
        // fail on Hibernate's version check and be misreported as a version conflict instead of "not found".
        if (productRepository.deleteProductById(id) == 0) {
            throw new ProductNotFoundException("Product not found: " + id);
        }
    }
}
