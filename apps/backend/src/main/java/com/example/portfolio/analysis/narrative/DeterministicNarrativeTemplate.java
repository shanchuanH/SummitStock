package com.example.portfolio.analysis.narrative;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class DeterministicNarrativeTemplate {
    public DecisionNarrative render(NarrativeInput input) {
        return new DecisionNarrative(
                headline(input.finalAction()),
                first(input.reasons(), "当前证据没有触发更高优先级的动作。"),
                limited(input.reasons(), 3, "结论来自已验证的持仓与组合规则。"),
                limited(input.risks(), 2, "后续证据变化可能改变结论。"),
                limited(input.changeConditions(), 2, "等待下一次有效证据更新。"),
                "置信度为 " + input.confidence() + "；叙述不改变确定性规则给出的动作、数量或止损。");
    }

    private static String headline(String action) {
        return switch (action) {
            case "BUY" -> "买入候选";
            case "STARTER_BUY" -> "小仓位试探";
            case "ADD" -> "可以加仓";
            case "HOLD" -> "继续持有";
            case "DO_NOT_ADD" -> "持有，暂不加仓";
            case "TRIM" -> "降低仓位";
            case "REDUCE_HALF" -> "减半风险";
            case "EXIT" -> "退出持仓";
            case "DEPLOY_DIP_TRANCHE" -> "分批部署核心仓位";
            case "PAUSE_NEW_RISK" -> "暂停新增风险";
            case "WAIT_FOR_DATA" -> "等待数据";
            case "WAIT_FOR_CONFIRMATION" -> "等待确认";
            case "WATCH" -> "继续观察";
            case "REVIEW" -> "需要复核";
            default -> "暂无动作";
        };
    }

    private static String first(List<String> values, String fallback) {
        return values.isEmpty() ? fallback : values.getFirst();
    }

    private static List<String> limited(List<String> values, int limit, String fallback) {
        return values.isEmpty()
                ? List.of(fallback)
                : values.stream().limit(limit).toList();
    }
}
