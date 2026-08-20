package com.example.portfolio.review;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PerformanceReviewStore {
    private final JdbcClient jdbc;

    PerformanceReviewStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    UUID userId(String email) {
        return jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email=:email AND status='ACTIVE'")
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    List<NavRow> nav(UUID userId) {
        return jdbc.sql(
                        """
                SELECT market_date marketDate,nav,account_equity accountEquity
                FROM portfolio_nav_snapshot WHERE user_id=UUID_TO_BIN(:userId)
                ORDER BY market_date,data_as_of
                """)
                .param("userId", userId.toString())
                .query(NavRow.class)
                .list();
    }

    List<PriceRow> benchmark(String symbol) {
        return jdbc.sql(
                        """
                SELECT b.market_date marketDate,b.close_price closePrice
                FROM price_bar b JOIN instrument i ON i.id=b.instrument_id
                WHERE i.symbol=:symbol AND b.timeframe='1D' AND b.adjusted=TRUE AND b.quality_status='HEALTHY'
                ORDER BY b.market_date,b.data_as_of
                """)
                .param("symbol", symbol)
                .query(PriceRow.class)
                .list();
    }

    List<PositionRow> positions(UUID userId) {
        return jdbc.sql(
                        """
                SELECT BIN_TO_UUID(p.id) positionId,i.symbol,p.classification,
                       DATE(s.data_as_of) marketDate,s.quantity,s.market_value marketValue
                FROM position_snapshot s JOIN position p ON p.id=s.position_id
                JOIN investment_account a ON a.id=p.account_id JOIN instrument i ON i.id=p.instrument_id
                WHERE a.user_id=UUID_TO_BIN(:userId)
                ORDER BY p.id,s.data_as_of,s.created_at
                """)
                .param("userId", userId.toString())
                .query(PositionRow.class)
                .list();
    }

    List<DecisionRow> decisions(UUID userId) {
        return jdbc.sql(
                        """
                SELECT BIN_TO_UUID(r.id) recommendationId,BIN_TO_UUID(r.position_id) positionId,
                       i.symbol,r.action,r.status,r.data_as_of dataAsOf,
                       ra.decision_type decisionType,ra.acknowledged_at decidedAt
                FROM recommendation r JOIN position p ON p.id=r.position_id
                JOIN instrument i ON i.id=p.instrument_id
                LEFT JOIN recommendation_acknowledgement ra ON ra.recommendation_id=r.id AND ra.user_id=r.user_id
                WHERE r.user_id=UUID_TO_BIN(:userId)
                ORDER BY r.data_as_of DESC LIMIT 50
                """)
                .param("userId", userId.toString())
                .query(DecisionRow.class)
                .list();
    }

    record NavRow(LocalDate marketDate, BigDecimal nav, BigDecimal accountEquity) {}

    record PriceRow(LocalDate marketDate, BigDecimal closePrice) {}

    record PositionRow(
            UUID positionId,
            String symbol,
            String classification,
            LocalDate marketDate,
            BigDecimal quantity,
            BigDecimal marketValue) {}

    record DecisionRow(
            UUID recommendationId,
            UUID positionId,
            String symbol,
            String action,
            String status,
            LocalDateTime dataAsOf,
            String decisionType,
            LocalDateTime decidedAt) {}
}
