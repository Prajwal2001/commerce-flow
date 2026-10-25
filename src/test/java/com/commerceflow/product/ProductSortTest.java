package com.commerceflow.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSortTest {

    @Test
    void parsesPropertyAndDirection() {
        assertThat(ProductSort.parse("price,desc")).isEqualTo(Sort.by(Sort.Direction.DESC, "price"));
        assertThat(ProductSort.parse("name,asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    void directionDefaultsToAscendingAndIsCaseInsensitive() {
        assertThat(ProductSort.parse("sku")).isEqualTo(Sort.by(Sort.Direction.ASC, "sku"));
        assertThat(ProductSort.parse("createdAt,DESC")).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertThat(ProductSort.parse(" price , desc ")).isEqualTo(Sort.by(Sort.Direction.DESC, "price"));
    }

    @Test
    void aTrailingCommaMeansNoDirection() {
        assertThat(ProductSort.parse("price,")).isEqualTo(Sort.by(Sort.Direction.ASC, "price"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "password,asc", "id", "", "price;drop table products", "Price"})
    void everyPropertyOutsideTheWhitelistIsRejectedAndNamesTheAllowedOnes(String sort) {
        assertThatThrownBy(() -> ProductSort.parse(sort))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("createdAt, name, price, sku");
    }

    @ParameterizedTest
    @ValueSource(strings = {"price,sideways", "price,1", "price,asc,desc"})
    void anInvalidDirectionOrAnExtraPartIsRejected(String sort) {
        assertThatThrownBy(() -> ProductSort.parse(sort)).isInstanceOf(InvalidSortException.class);
    }
}
