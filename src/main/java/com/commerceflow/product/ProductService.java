package com.commerceflow.product;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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
            if (violatesSkuConstraint(e)) {
                throw new DuplicateSkuException("Product with SKU '" + request.sku() + "' already exists");
            }
            throw e;
        }
    }

    /** The constraint name is the contract with V1__create_products.sql; walk the cause chain because wrapping depth can vary. */
    private static boolean violatesSkuConstraint(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && SKU_CONSTRAINT.equals(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
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

        return new PageResponse<>(productResponseList, pageable.getPageNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));

        product.updateData(request.name(), request.description(), request.price());

        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));

        productRepository.delete(product);
    }
}
