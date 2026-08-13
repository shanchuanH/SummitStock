package com.example.portfolio.analysis.risk;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DrawdownAttributionService {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private final JdbcClient jdbc;

    public DrawdownAttributionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Attribution calculate(
            UUID userId, LocalDate peakMarketDate, LocalDate marketDate, BigDecimal peakAccountEquity) {
        if (peakMarketDate == null || !peakMarketDate.isBefore(marketDate) || peakAccountEquity.signum() <= 0) {
            return Attribution.empty();
        }
        var positions = losses(userId, peakMarketDate, marketDate);
        var clusters = clusterLosses(userId, peakMarketDate, marketDate);
        return summarize(positions, clusters, peakAccountEquity);
    }

    static Attribution summarize(List<LossRow> positions, List<LossRow> clusters, BigDecimal peakAccountEquity) {
        var totalLoss = positions.stream().map(LossRow::lossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var largestPositionShare = largestShare(positions, totalLoss);
        var totalClusterLoss = clusters.stream().map(LossRow::lossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Attribution(
                json(positions, peakAccountEquity),
                json(clusters, peakAccountEquity),
                largestPositionShare,
                largestShare(clusters, totalClusterLoss));
    }

    private List<LossRow> losses(UUID userId, LocalDate peakDate, LocalDate marketDate) {
        return jdbc.sql(
                        """
                        SELECT i.symbol label,
                               GREATEST((peak.close_price-current.close_price)*p.quantity,0) lossAmount
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        JOIN instrument i ON i.id=p.instrument_id
                        JOIN price_bar peak ON peak.instrument_id=i.id AND peak.adjusted=TRUE
                          AND peak.market_date=(SELECT MAX(x.market_date) FROM price_bar x
                                                WHERE x.instrument_id=i.id AND x.adjusted=TRUE AND x.market_date<=:peakDate)
                        JOIN price_bar current ON current.instrument_id=i.id AND current.adjusted=TRUE
                          AND current.market_date=(SELECT MAX(x.market_date) FROM price_bar x
                                                   WHERE x.instrument_id=i.id AND x.adjusted=TRUE AND x.market_date<=:marketDate)
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                          AND current.close_price<peak.close_price
                        ORDER BY lossAmount DESC,i.symbol
                        """)
                .param("userId", userId.toString())
                .param("peakDate", peakDate)
                .param("marketDate", marketDate)
                .query(LossRow.class)
                .list();
    }

    private List<LossRow> clusterLosses(UUID userId, LocalDate peakDate, LocalDate marketDate) {
        return jdbc.sql(
                        """
                        SELECT c.display_name label,
                               SUM(GREATEST((peak.close_price-current.close_price)*p.quantity,0)*m.contribution_weight) lossAmount
                        FROM risk_cluster c JOIN risk_cluster_membership m ON m.risk_cluster_id=c.id
                        JOIN position p ON p.id=m.position_id AND p.status='OPEN'
                        JOIN price_bar peak ON peak.instrument_id=p.instrument_id AND peak.adjusted=TRUE
                          AND peak.market_date=(SELECT MAX(x.market_date) FROM price_bar x
                                                WHERE x.instrument_id=p.instrument_id AND x.adjusted=TRUE AND x.market_date<=:peakDate)
                        JOIN price_bar current ON current.instrument_id=p.instrument_id AND current.adjusted=TRUE
                          AND current.market_date=(SELECT MAX(x.market_date) FROM price_bar x
                                                   WHERE x.instrument_id=p.instrument_id AND x.adjusted=TRUE AND x.market_date<=:marketDate)
                        WHERE c.user_id=UUID_TO_BIN(:userId)
                        GROUP BY c.id,c.display_name HAVING lossAmount>0
                        ORDER BY lossAmount DESC,c.display_name
                        """)
                .param("userId", userId.toString())
                .param("peakDate", peakDate)
                .param("marketDate", marketDate)
                .query(LossRow.class)
                .list();
    }

    private static double largestShare(List<LossRow> values, BigDecimal total) {
        if (values.isEmpty() || total.signum() <= 0) return 0;
        return values.stream()
                .map(LossRow::lossAmount)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO)
                .divide(total, MATH)
                .doubleValue();
    }

    private static String json(List<LossRow> values, BigDecimal peakEquity) {
        return values.stream()
                .map(value -> "{\"label\":\"" + escape(value.label()) + "\",\"lossAmount\":"
                        + value.lossAmount().toPlainString() + ",\"contributionFraction\":"
                        + value.lossAmount().negate().divide(peakEquity, MATH).toPlainString() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record LossRow(String label, BigDecimal lossAmount) {}

    public record Attribution(
            String positionJson, String clusterJson, double largestPositionLossShare, double largestClusterLossShare) {
        static Attribution empty() {
            return new Attribution("[]", "[]", 0, 0);
        }
    }
}
