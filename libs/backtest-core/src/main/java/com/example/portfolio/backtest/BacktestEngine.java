package com.example.portfolio.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BacktestEngine {
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    public Result replay(Request request) {
        validate(request);
        var bars =
                request.bars().stream().sorted(Comparator.comparing(Bar::date)).toList();
        var cash = request.startingCash();
        var positions = new HashMap<String, BigDecimal>();
        var trades = new ArrayList<Trade>();

        for (var bar : bars) {
            for (var action : request.corporateActions()) {
                if (!action.symbol().equals(bar.symbol())
                        || !action.effectiveDate().equals(bar.date())) continue;
                var held = positions.getOrDefault(action.symbol(), BigDecimal.ZERO);
                if (action.type() == CorporateActionType.SPLIT) {
                    positions.put(action.symbol(), held.multiply(action.value()));
                } else {
                    cash = cash.add(held.multiply(action.value()));
                }
            }
            for (var signal : request.signals()) {
                if (!signal.symbol().equals(bar.symbol()) || !bar.date().isAfter(signal.signalDate())) continue;
                if (trades.stream().anyMatch(t -> t.signalId().equals(signal.id()))) continue;
                var lifecycle = lifecycle(request, signal.symbol());
                if (bar.date().isBefore(lifecycle.listedOn()) || bar.date().isAfter(lifecycle.delistedOn())) continue;
                if (signal.action() == Action.HOLD) continue;
                var quantity = signal.quantity().min(request.maximumFillQuantity());
                var compliant = signal.quantity().compareTo(request.maximumFillQuantity()) <= 0;
                var multiplier = signal.action() == Action.BUY
                        ? BigDecimal.ONE.add(request.slippageBps().divide(TEN_THOUSAND, 12, RoundingMode.HALF_EVEN))
                        : BigDecimal.ONE.subtract(
                                request.slippageBps().divide(TEN_THOUSAND, 12, RoundingMode.HALF_EVEN));
                var fillPrice = bar.open().multiply(multiplier).setScale(6, RoundingMode.HALF_EVEN);
                if (signal.action() == Action.BUY) {
                    var affordable = cash.divide(fillPrice, 8, RoundingMode.DOWN);
                    quantity = quantity.min(affordable);
                    cash = cash.subtract(quantity.multiply(fillPrice));
                    positions.merge(signal.symbol(), quantity, BigDecimal::add);
                } else {
                    quantity = quantity.min(positions.getOrDefault(signal.symbol(), BigDecimal.ZERO));
                    cash = cash.add(quantity.multiply(fillPrice));
                    positions.merge(signal.symbol(), quantity.negate(), BigDecimal::add);
                }
                trades.add(new Trade(
                        signal.id(),
                        signal.symbol(),
                        signal.sleeve(),
                        signal.action(),
                        signal.signalDate(),
                        bar.date(),
                        quantity,
                        fillPrice,
                        compliant,
                        request.outOfSampleFrom() != null && !bar.date().isBefore(request.outOfSampleFrom())));
            }
        }

        var lastClose = new HashMap<String, BigDecimal>();
        bars.forEach(bar -> lastClose.put(bar.symbol(), bar.close()));
        var equity = cash;
        for (var entry : positions.entrySet()) {
            equity = equity.add(entry.getValue().multiply(lastClose.getOrDefault(entry.getKey(), BigDecimal.ZERO)));
        }
        return new Result(
                List.copyOf(trades),
                cash.setScale(2, RoundingMode.HALF_EVEN),
                equity.setScale(2, RoundingMode.HALF_EVEN),
                Map.copyOf(positions),
                trades.stream().allMatch(Trade::quantityCompliant),
                trades.stream().filter(Trade::outOfSample).count());
    }

    private static void validate(Request request) {
        if (request.startingCash().signum() < 0
                || request.slippageBps().signum() < 0
                || request.maximumFillQuantity().signum() <= 0)
            throw new IllegalArgumentException("INVALID_BACKTEST_CONFIG");
        if (request.bars().stream().anyMatch(bar -> !bar.completed()))
            throw new IllegalArgumentException("BACKTEST_COMPLETED_BARS_ONLY");
        if (!request.universeIncludesDelisted()) throw new IllegalArgumentException("BACKTEST_SURVIVORSHIP_BIAS");
        if (request.outOfSampleFrom() != null
                && request.trainingDataThrough() != null
                && !request.trainingDataThrough().isBefore(request.outOfSampleFrom()))
            throw new IllegalArgumentException("BACKTEST_OOS_LEAKAGE");
        var distinct = request.bars().stream()
                .map(bar -> bar.symbol() + "@" + bar.date())
                .distinct()
                .count();
        if (distinct != request.bars().size()) throw new IllegalArgumentException("BACKTEST_DUPLICATE_BAR");
        request.signals().forEach(signal -> {
            if (signal.quantity().signum() < 0) throw new IllegalArgumentException("BACKTEST_NEGATIVE_QUANTITY");
        });
    }

    private static Lifecycle lifecycle(Request request, String symbol) {
        return request.lifecycles().stream()
                .filter(item -> item.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("BACKTEST_MISSING_LIFECYCLE:" + symbol));
    }

    public record Request(
            BigDecimal startingCash,
            BigDecimal slippageBps,
            BigDecimal maximumFillQuantity,
            boolean universeIncludesDelisted,
            LocalDate trainingDataThrough,
            LocalDate outOfSampleFrom,
            List<Bar> bars,
            List<Signal> signals,
            List<CorporateAction> corporateActions,
            List<Lifecycle> lifecycles) {
        public Request {
            bars = List.copyOf(bars);
            signals = List.copyOf(signals);
            corporateActions = List.copyOf(corporateActions);
            lifecycles = List.copyOf(lifecycles);
        }
    }

    public record Bar(
            String symbol,
            LocalDate date,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            boolean completed) {
        public Bar {
            Objects.requireNonNull(symbol);
            Objects.requireNonNull(date);
        }
    }

    public record Signal(
            String id,
            String symbol,
            LocalDate signalDate,
            Action action,
            Sleeve sleeve,
            BigDecimal quantity,
            String priority) {}

    public record CorporateAction(String symbol, LocalDate effectiveDate, CorporateActionType type, BigDecimal value) {}

    public record Lifecycle(String symbol, LocalDate listedOn, LocalDate delistedOn, boolean etf) {}

    public record Trade(
            String signalId,
            String symbol,
            Sleeve sleeve,
            Action action,
            LocalDate signalDate,
            LocalDate fillDate,
            BigDecimal quantity,
            BigDecimal fillPrice,
            boolean quantityCompliant,
            boolean outOfSample) {}

    public record Result(
            List<Trade> trades,
            BigDecimal endingCash,
            BigDecimal endingEquity,
            Map<String, BigDecimal> positions,
            boolean quantityCompliant,
            long outOfSampleTrades) {}

    public enum Action {
        BUY,
        SELL,
        HOLD
    }

    public enum Sleeve {
        CORE,
        ACTIVE
    }

    public enum CorporateActionType {
        SPLIT,
        CASH_DIVIDEND
    }
}
