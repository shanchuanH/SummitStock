package com.example.portfolio.quant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuantDependencyBoundaryTest {
    @Test
    void coreClasspathHasNoSpringFramework() {
        assertThatThrownBy(() -> Class.forName("org.springframework.context.ApplicationContext"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
