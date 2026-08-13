package com.example.portfolio.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/settings/preferences")
public class OwnerPreferenceController {
    private final JdbcClient jdbc;
    private final Clock clock;

    public OwnerPreferenceController(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping
    PreferenceResponse get(Principal principal) {
        return jdbc.sql(
                        """
                        SELECT emergency_cash_target emergencyCashTarget,manual_execution_broker manualExecutionBroker,
                               notification_preference notificationPreference,starter_buy_preference starterBuyPreference,
                               primary_etf_preference primaryEtfPreference,personal_trade_risk_cap personalTradeRiskCap,
                               p.version,p.updated_at updatedAt FROM owner_preference p JOIN app_user u ON u.id=p.user_id
                        WHERE u.email=:email
                        """)
                .param("email", principal.getName())
                .query(PreferenceRow.class)
                .optional()
                .map(PreferenceResponse::from)
                .orElse(new PreferenceResponse(null, "FIDELITY", "IN_APP", "STRATEGY_DEFAULT", "QQQM", null, 0, null));
    }

    @PutMapping
    @Transactional
    PreferenceResponse update(@Valid @RequestBody PreferenceRequest request, Principal principal) {
        var now = clock.instant();
        long currentVersion = jdbc.sql(
                        """
                        SELECT COALESCE((SELECT p.version FROM owner_preference p WHERE p.user_id=u.id FOR UPDATE),0)
                        FROM app_user u WHERE u.email=:email
                        """)
                .param("email", principal.getName())
                .query(Long.class)
                .single();
        if (currentVersion != request.expectedVersion())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preference version changed");
        int changed = jdbc.sql(
                        """
                        INSERT INTO owner_preference (user_id,emergency_cash_target,manual_execution_broker,
                          notification_preference,starter_buy_preference,primary_etf_preference,personal_trade_risk_cap,version,updated_at)
                        SELECT u.id,:cash,:broker,:notification,:starter,:etf,:risk,1,:now FROM app_user u
                        WHERE u.email=:email
                        ON DUPLICATE KEY UPDATE emergency_cash_target=VALUES(emergency_cash_target),
                          manual_execution_broker=VALUES(manual_execution_broker),notification_preference=VALUES(notification_preference),
                          starter_buy_preference=VALUES(starter_buy_preference),primary_etf_preference=VALUES(primary_etf_preference),
                          personal_trade_risk_cap=VALUES(personal_trade_risk_cap),updated_at=VALUES(updated_at),version=owner_preference.version+1
                        """)
                .param("cash", request.emergencyCashTarget())
                .param("broker", request.manualExecutionBroker())
                .param("notification", request.notificationPreference())
                .param("starter", request.starterBuyPreference())
                .param("etf", request.primaryEtfPreference())
                .param("risk", request.personalTradeRiskCap())
                .param("version", request.expectedVersion())
                .param("now", now)
                .param("email", principal.getName())
                .update();
        var result = get(principal);
        if (changed == 0 || result.version() != request.expectedVersion() + 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preference version changed");
        jdbc.sql(
                        """
                        INSERT INTO audit_log (id,user_id,event_type,entity_type,entity_id,rule_ids,details,occurred_at)
                        SELECT UUID_TO_BIN(:id),u.id,'OWNER_PREFERENCE_UPDATED','OWNER_PREFERENCE',:entity,
                               JSON_ARRAY('OWNER.PREFERENCE.VERSIONED.001'),JSON_OBJECT('version',:version),:now
                        FROM app_user u WHERE u.email=:email
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("entity", principal.getName())
                .param("version", result.version())
                .param("now", now)
                .param("email", principal.getName())
                .update();
        return result;
    }

    public record PreferenceRequest(
            @PositiveOrZero BigDecimal emergencyCashTarget,
            @NotBlank @Pattern(regexp = "FIDELITY|OTHER") String manualExecutionBroker,
            @NotBlank @Pattern(regexp = "IN_APP|EMAIL|NONE") String notificationPreference,
            @NotBlank @Pattern(regexp = "STRATEGY_DEFAULT|CONSERVATIVE|DISABLED") String starterBuyPreference,
            @NotBlank @Pattern(regexp = "QQQM|VTI|SPY") String primaryEtfPreference,
            @DecimalMin("0.001") @DecimalMax("0.01") BigDecimal personalTradeRiskCap,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record PreferenceResponse(
            String emergencyCashTarget,
            String manualExecutionBroker,
            String notificationPreference,
            String starterBuyPreference,
            String primaryEtfPreference,
            String personalTradeRiskCap,
            long version,
            Instant updatedAt) {
        static PreferenceResponse from(PreferenceRow row) {
            return new PreferenceResponse(
                    decimal(row.emergencyCashTarget()),
                    row.manualExecutionBroker(),
                    row.notificationPreference(),
                    row.starterBuyPreference(),
                    row.primaryEtfPreference(),
                    decimal(row.personalTradeRiskCap()),
                    row.version(),
                    row.updatedAt() == null ? null : row.updatedAt().toInstant(java.time.ZoneOffset.UTC));
        }
    }

    record PreferenceRow(
            BigDecimal emergencyCashTarget,
            String manualExecutionBroker,
            String notificationPreference,
            String starterBuyPreference,
            String primaryEtfPreference,
            BigDecimal personalTradeRiskCap,
            long version,
            java.time.LocalDateTime updatedAt) {}

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
