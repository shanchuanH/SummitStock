package com.example.portfolio.analysis.risk;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DrawdownAttributionService {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.00000001");
    private final JdbcClient jdbc;

    public DrawdownAttributionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Attribution calculate(
            UUID userId, LocalDate peakMarketDate, LocalDate marketDate, BigDecimal ignoredPeakAccountEquity) {
        if (peakMarketDate == null || !peakMarketDate.isBefore(marketDate)) return Attribution.empty();
        var navLoss = navDrawdownLoss(userId, marketDate);
        if (navLoss.signum() <= 0) return Attribution.empty();
        var positions = losses(userId, peakMarketDate, marketDate);
        return summarize(positions, clusterLosses(userId, positions), navLoss);
    }

    static Attribution summarize(List<LossRow> positions, List<LossRow> clusters, BigDecimal navDrawdownLoss) {
        var positionContribution = positions.stream().map(LossRow::lossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var otherContribution = navDrawdownLoss.subtract(positionContribution);
        var reconciled = new ArrayList<>(positions);
        if (otherContribution.signum() != 0) {
            reconciled.add(new LossRow(null, "CASH_AND_OTHER", otherContribution, "OTHER"));
        }
        return new Attribution(
                json(reconciled, navDrawdownLoss),
                json(clusters, navDrawdownLoss),
                largestShare(positions, navDrawdownLoss),
                largestShare(clusters, navDrawdownLoss),
                positionContribution,
                otherContribution);
    }

    private BigDecimal navDrawdownLoss(UUID userId, LocalDate marketDate) {
        return jdbc.sql(
                        """
                        SELECT GREATEST((high_water_nav-nav)*units,0)
                        FROM portfolio_nav_snapshot
                        WHERE user_id=UUID_TO_BIN(:userId) AND market_date<=:marketDate
                        ORDER BY market_date DESC,data_as_of DESC LIMIT 1
                        """)
                .param("userId", userId.toString())
                .param("marketDate", marketDate)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    private List<LossRow> losses(UUID userId, LocalDate peakDate, LocalDate marketDate) {
        return jdbc
                .sql(
                        """
                        WITH owned AS (
                          SELECT p.id,p.instrument_id,i.symbol,p.opened_at,p.closed_at,
                            CASE WHEN DATE(p.opened_at)>:peakDate THEN 0 ELSE
                              (SELECT s.quantity FROM position_snapshot s WHERE s.position_id=p.id
                               AND DATE(s.data_as_of)<=:peakDate ORDER BY s.data_as_of DESC LIMIT 1) END peak_qty,
                            CASE WHEN p.closed_at IS NOT NULL AND DATE(p.closed_at)<=:marketDate THEN 0 ELSE
                              (SELECT s.quantity FROM position_snapshot s WHERE s.position_id=p.id
                               AND DATE(s.data_as_of)<=:marketDate ORDER BY s.data_as_of DESC LIMIT 1) END current_qty
                          FROM position p JOIN investment_account a ON a.id=p.account_id
                          JOIN instrument i ON i.id=p.instrument_id
                          WHERE a.user_id=UUID_TO_BIN(:userId) AND DATE(p.opened_at)<=:marketDate
                            AND (p.closed_at IS NULL OR DATE(p.closed_at)>:peakDate)
                        ), ledger AS (
                          SELECT o.id,COALESCE(SUM(j.quantity_delta),0) quantity_delta,
                            COALESCE(SUM(j.quantity_delta*j.execution_price),0) capital_flow,
                            SUM(j.id IS NOT NULL AND (j.quantity_delta IS NULL OR j.execution_price IS NULL)) incomplete_rows
                          FROM owned o LEFT JOIN trade_journal j ON j.position_id=o.id
                            AND DATE(j.occurred_at)>:peakDate AND DATE(j.occurred_at)<=:marketDate
                          GROUP BY o.id
                        )
                        SELECT BIN_TO_UUID(o.id) positionId,o.symbol label,o.peak_qty peakQuantity,
                          peak.close_price peakPrice,o.current_qty currentQuantity,current.close_price currentPrice,
                          l.quantity_delta quantityDelta,l.capital_flow capitalFlow,l.incomplete_rows incompleteRows
                        FROM owned o JOIN ledger l ON l.id=o.id
                        JOIN price_bar peak ON peak.instrument_id=o.instrument_id AND peak.adjusted=TRUE
                          AND peak.market_date=(SELECT MAX(x.market_date) FROM price_bar x WHERE x.instrument_id=o.instrument_id
                            AND x.adjusted=TRUE AND x.market_date<=:peakDate)
                        JOIN price_bar current ON current.instrument_id=o.instrument_id AND current.adjusted=TRUE
                          AND current.market_date=(SELECT MAX(x.market_date) FROM price_bar x WHERE x.instrument_id=o.instrument_id
                            AND x.adjusted=TRUE AND x.market_date<=:marketDate)
                        WHERE o.peak_qty IS NOT NULL AND o.current_qty IS NOT NULL
                        ORDER BY o.symbol
                        """)
                .param("userId", userId.toString())
                .param("peakDate", peakDate)
                .param("marketDate", marketDate)
                .query(LossEquation.class)
                .list()
                .stream()
                .map(DrawdownAttributionService::loss)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(LossRow::lossAmount).reversed().thenComparing(LossRow::label))
                .toList();
    }

    static java.util.Optional<LossRow> loss(LossEquation value) {
        if (value.incompleteRows() > 0
                || value.peakQuantity()
                                .add(value.quantityDelta())
                                .subtract(value.currentQuantity())
                                .abs()
                                .compareTo(QUANTITY_TOLERANCE)
                        > 0) {
            return java.util.Optional.empty();
        }
        var pnl = value.currentQuantity()
                .multiply(value.currentPrice())
                .subtract(value.peakQuantity().multiply(value.peakPrice()))
                .subtract(value.capitalFlow());
        var loss = pnl.negate();
        return loss.signum() == 0
                ? java.util.Optional.empty()
                : java.util.Optional.of(new LossRow(value.positionId(), value.label(), loss, "POSITION"));
    }

    private List<LossRow> clusterLosses(UUID userId, List<LossRow> positions) {
        if (positions.isEmpty()) return List.of();
        var losses = new HashMap<UUID, BigDecimal>();
        positions.forEach(value -> losses.put(value.positionId(), value.lossAmount()));
        var totals = new HashMap<String, BigDecimal>();
        for (var membership : jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(m.position_id) positionId,c.display_name label,m.contribution_weight weight
                        FROM risk_cluster_membership m JOIN risk_cluster c ON c.id=m.risk_cluster_id
                        WHERE c.user_id=UUID_TO_BIN(:userId)
                        """)
                .param("userId", userId.toString())
                .query(ClusterMembership.class)
                .list()) {
            var loss = losses.get(membership.positionId());
            if (loss != null) totals.merge(membership.label(), loss.multiply(membership.weight()), BigDecimal::add);
        }
        var result = new ArrayList<LossRow>();
        totals.forEach((label, loss) -> result.add(new LossRow(null, label, loss, "CLUSTER")));
        result.sort(Comparator.comparing(LossRow::lossAmount).reversed().thenComparing(LossRow::label));
        return result;
    }

    private static double largestShare(List<LossRow> values, BigDecimal navDrawdownLoss) {
        if (values.isEmpty() || navDrawdownLoss.signum() <= 0) return 0;
        return values.stream()
                .map(LossRow::lossAmount)
                .filter(value -> value.signum() > 0)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO)
                .divide(navDrawdownLoss, MATH)
                .doubleValue();
    }

    private static String json(List<LossRow> values, BigDecimal navDrawdownLoss) {
        return values.stream()
                .map(value -> "{\"label\":\"" + escape(value.label()) + "\",\"contributionType\":\""
                        + value.contributionType() + "\",\"lossContributionAmount\":"
                        + value.lossAmount().toPlainString() + ",\"contributionFraction\":"
                        + value.lossAmount().divide(navDrawdownLoss, MATH).toPlainString() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record ClusterMembership(UUID positionId, String label, BigDecimal weight) {}

    record LossEquation(
            UUID positionId,
            String label,
            BigDecimal peakQuantity,
            BigDecimal peakPrice,
            BigDecimal currentQuantity,
            BigDecimal currentPrice,
            BigDecimal quantityDelta,
            BigDecimal capitalFlow,
            long incompleteRows) {}

    record LossRow(UUID positionId, String label, BigDecimal lossAmount, String contributionType) {
        LossRow(String label, BigDecimal lossAmount) {
            this(null, label, lossAmount, "POSITION");
        }
    }

    public record Attribution(
            String positionJson,
            String clusterJson,
            double largestPositionLossShare,
            double largestClusterLossShare,
            BigDecimal positionContribution,
            BigDecimal otherContribution) {
        static Attribution empty() {
            return new Attribution("[]", "[]", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
