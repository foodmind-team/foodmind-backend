package com.foodmind.foodmindbackend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void maximumPageIndexDoesNotOverflowHasNextCalculation() {
        PageResponse<Object> response = PageResponse.of(
                List.of(), Integer.MAX_VALUE, 1, Long.MAX_VALUE);

        assertThat(response.totalPages()).isEqualTo(Integer.MAX_VALUE);
        assertThat(response.hasNext()).isFalse();
    }
}
