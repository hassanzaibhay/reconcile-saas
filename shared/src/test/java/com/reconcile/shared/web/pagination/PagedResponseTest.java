package com.reconcile.shared.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PagedResponseTest {

    @Test
    void nullItemsBecomeEmptyList() {
        assertThat(new PagedResponse<>(null, null, false).items()).isEmpty();
    }

    @Test
    void itemsAreDefensivelyCopied() {
        List<String> src = new ArrayList<>(List.of("a"));
        PagedResponse<String> page = PagedResponse.of(src, "cur", true);
        src.add("b");

        assertThat(page.items()).containsExactly("a");
        assertThatThrownBy(() -> page.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
    }
}
