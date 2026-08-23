package com.example.portfolio.context;

import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.market.MarketRegimeEngine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public record CanonicalMarketRegimeEvidence(
        double trend,
        double momentum,
        double breadth,
        double stressResilience,
        boolean spyBelow200Day,
        boolean qqqBelow200Day,
        double vix,
        double breadth50,
        boolean qqqMacdNegative,
        double qqqRsi,
        boolean narrowRally,
        EvidenceQuality quality) {

    public CanonicalMarketRegimeEvidence {
        finite(trend, "trend");
        finite(momentum, "momentum");
        finite(breadth, "breadth");
        finite(stressResilience, "stressResilience");
        finite(vix, "vix");
        finite(breadth50, "breadth50");
        finite(qqqRsi, "qqqRsi");
        if (quality == null) throw new IllegalArgumentException("quality is required");
    }

    public static CanonicalMarketRegimeEvidence from(MarketRegimeEngine.Input input) {
        return new CanonicalMarketRegimeEvidence(
                input.trend(),
                input.momentum(),
                input.breadth(),
                input.stressResilience(),
                input.spyBelow200Day(),
                input.qqqBelow200Day(),
                input.vix(),
                input.breadth50(),
                input.qqqMacdNegative(),
                input.qqqRsi(),
                input.narrowRally(),
                input.quality());
    }

    public String canonicalJson() {
        return "{" + "\"trend\":" + number(trend)
                + ",\"momentum\":" + number(momentum)
                + ",\"breadth\":" + number(breadth)
                + ",\"stressResilience\":" + number(stressResilience)
                + ",\"spyBelow200Day\":" + spyBelow200Day
                + ",\"qqqBelow200Day\":" + qqqBelow200Day
                + ",\"vix\":" + number(vix)
                + ",\"breadth50\":" + number(breadth50)
                + ",\"qqqMacdNegative\":" + qqqMacdNegative
                + ",\"qqqRsi\":" + number(qqqRsi)
                + ",\"narrowRally\":" + narrowRally
                + ",\"quality\":\"" + quality.name() + "\"}";
    }

    public String checksum(String strategyVersion, Instant dataAsOf) {
        return sha256(strategyVersion + ":regime:" + dataAsOf + ":" + canonicalJson());
    }

    private static String number(double value) {
        var decimal = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        return decimal.signum() == 0 ? "0" : decimal.toPlainString();
    }

    private static void finite(double value, String field) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
