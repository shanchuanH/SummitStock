package com.example.portfolio.portfolio;

import com.example.portfolio.strategy.position.IntradayBreachEvaluator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntradayStopAlertService {
    private final JdbcClient jdbc;
    private final Clock clock;

    public IntradayStopAlertService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public int evaluateLatestQuotes() {
        int affected = 0;
        for (var value : jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) positionId, BIN_TO_UUID(s.id) stopSnapshotId,
                               q.last_price observedPrice, s.catastrophic_stop catastrophicStop
                        FROM position p
                        JOIN stop_snapshot s ON s.id=(SELECT x.id FROM stop_snapshot x
                            WHERE x.position_id=p.id ORDER BY x.data_as_of DESC, x.created_at DESC LIMIT 1)
                        JOIN quote q ON q.id=(SELECT y.id FROM quote y WHERE y.instrument_id=p.instrument_id
                            ORDER BY y.data_as_of DESC, y.created_at DESC LIMIT 1)
                        WHERE p.status='OPEN'
                        """)
                .query(IntradayEvidence.class)
                .list()) {
            var result = IntradayBreachEvaluator.evaluate(value.observedPrice(), value.catastrophicStop());
            if (!result.breached()) continue;
            affected += jdbc.sql(
                            """
                            INSERT IGNORE INTO stop_alert (
                                id, position_id, stop_snapshot_id, event_type, market_date,
                                observed_price, acknowledged_at, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:positionId), UUID_TO_BIN(:stopId),
                                'CATASTROPHIC', :marketDate, :price, NULL, :createdAt
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("positionId", value.positionId().toString())
                    .param("stopId", value.stopSnapshotId().toString())
                    .param("marketDate", LocalDate.now(clock))
                    .param("price", value.observedPrice())
                    .param("createdAt", clock.instant())
                    .update();
        }
        return affected;
    }

    record IntradayEvidence(
            UUID positionId,
            UUID stopSnapshotId,
            java.math.BigDecimal observedPrice,
            java.math.BigDecimal catastrophicStop) {}
}
