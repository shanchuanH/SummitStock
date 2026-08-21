package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.portfolio.portfolioimport.application.PortfolioImportConfirmationService.CashLocation;
import com.example.portfolio.portfolioimport.application.PortfolioImportConfirmationService.CashSetup;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EmergencyCashSetupTest {
    private static final BigDecimal FLOOR = new BigDecimal("20000");

    @Test
    void splitKeepsFidelityAndExternalAmountsIndependent() {
        var setup = new CashSetup(CashLocation.SPLIT, new BigDecimal("10000"), new BigDecimal("10000"));

        setup.validateAgainst(new BigDecimal("30000"), FLOOR);

        assertThat(setup.fidelityAmount()).isEqualByComparingTo("10000");
        assertThat(setup.externalAmount()).isEqualByComparingTo("10000");
        assertThat(setup.totalAmount()).isEqualByComparingTo("20000");
    }

    @Test
    void splitRejectsFidelityAmountAboveImportedCash() {
        var setup = new CashSetup(CashLocation.SPLIT, new BigDecimal("20000"), new BigDecimal("5000"));

        assertThatThrownBy(() -> setup.validateAgainst(new BigDecimal("15000"), FLOOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds imported Fidelity cash");
    }

    @Test
    void fidelityOnlyRejectsExternalAmount() {
        var setup = new CashSetup(CashLocation.IN_FIDELITY, new BigDecimal("10000"), BigDecimal.ONE);

        assertThatThrownBy(() -> setup.validateAgainst(new BigDecimal("30000"), FLOOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("external amount to be zero");
    }

    @Test
    void externalOnlyRejectsFidelityAmount() {
        var setup = new CashSetup(CashLocation.EXTERNAL_BANK, BigDecimal.ONE, new BigDecimal("10000"));

        assertThatThrownBy(() -> setup.validateAgainst(new BigDecimal("30000"), FLOOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fidelity amount to be zero");
    }
}
