package com.example.portfolio.earnings;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.market.provider.ProviderCallException;
import com.example.portfolio.strategy.position.EarningsPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EarningsIntelligenceApplicationService {
    private final EarningsCalendarProvider provider;
    private final EarningsEvidenceStore store;
    private final PortfolioProperties properties;
    private final Clock clock;
    private final EarningsReactionCalculator calculator = new EarningsReactionCalculator();
    private final EarningsReactionStats statsEngine = new EarningsReactionStats();
    private final EarningsEventRiskEngine riskEngine = new EarningsEventRiskEngine();

    public EarningsIntelligenceApplicationService(
            EarningsCalendarProvider provider,
            EarningsEvidenceStore store,
            PortfolioProperties properties,
            Clock clock) {
        this.provider = provider;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    public CollectionResult collectCalendar() {
        var from = LocalDate.now(clock);
        var to = from.plusDays(90);
        int observations = 0;
        int affected = 0;
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var instrument : store.eligibleInstruments()) {
            final EarningsCalendarProvider.CalendarResult result;
            try {
                result = provider.fetch(instrument.symbol(), from, to);
            } catch (ProviderCallException exception) {
                failed.add(instrument.symbol());
                warnings.add(instrument.symbol() + ":" + exception.code());
                continue;
            }
            var bounded = result.events().stream()
                    .filter(event -> !event.marketDate().isBefore(from)
                            && !event.marketDate().isAfter(to))
                    .toList();
            observations += bounded.size();
            for (var event : bounded) {
                affected += store.saveEvent(
                        instrument.id(),
                        event,
                        result.provider(),
                        result.quality().name());
            }
        }
        return new CollectionResult(observations, affected, failed, warnings);
    }

    public int computeReactions() {
        int affected = 0;
        for (var event : store.eventsWithoutReaction()) {
            var value = calculator.calculate(
                    event.marketDate(), timing(event.timing()), store.bars(event.instrumentId(), event.marketDate()));
            if (value != null) affected += store.saveReaction(event, value);
        }
        return affected;
    }

    public int computeRisk() {
        int affected = 0;
        for (var input : store.positionRiskInputs()) {
            var stats = statsEngine.summarize(store.recentReactions(input.instrumentId()));
            var risk = riskEngine.assess(new EarningsEventRiskEngine.Input(
                    stats,
                    zero(input.positionWeight()),
                    zero(input.profitCushionR()),
                    input.holdingClassification(),
                    input.binaryEvent()));
            var policy = EarningsPolicy.review(new EarningsPolicy.Input(
                    input.holdingClassification(),
                    stats.eventCount(),
                    zero(input.profitCushionR()),
                    zero(input.positionWeight()).compareTo(new BigDecimal("0.12")) > 0,
                    EarningsPolicy.EventRisk.valueOf(risk.name()),
                    input.binaryEvent()));
            affected += store.saveRisk(input, stats, risk, policy, properties.strategyVersion());
        }
        return affected;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static EarningsCalendarProvider.Timing timing(String value) {
        try {
            return EarningsCalendarProvider.Timing.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return EarningsCalendarProvider.Timing.UNKNOWN;
        }
    }

    public record CollectionResult(
            int observations, int affected, List<String> failedInstruments, List<String> warnings) {
        public CollectionResult {
            failedInstruments = List.copyOf(failedInstruments);
            warnings = List.copyOf(warnings);
        }

        public CollectionResult(int observations, int affected) {
            this(observations, affected, List.of(), List.of());
        }
    }
}
