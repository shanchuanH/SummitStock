import type { components } from "@portfolio/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { postJson } from "../http";
import { presentAction } from "../presentation/action-presentation";
import { presentClassification } from "../presentation/classification-presentation";
import { presentConfidence } from "../presentation/confidence-presentation";
import { formatDateTime } from "../presentation/date-format";
import {
  formatMoney,
  formatPercent,
  formatQuantity,
} from "../presentation/number-format";
import { presentPriority } from "../presentation/priority-presentation";
import { presentReason } from "../presentation/reason-presentation";

export type DashboardAction = components["schemas"]["BriefAction"];

const reasonTagOptions = [
  ["NEW_FUNDAMENTAL_EVIDENCE", "新的基本面证据"],
  ["VALUATION", "估值"],
  ["PRICE_CONFIRMATION", "价格确认"],
  ["CATALYST", "催化剂"],
  ["RISK_REDUCTION", "降低风险"],
  ["COST_BASIS_ANCHOR", "成本价锚定"],
  ["HISTORICAL_HIGH_ANCHOR", "历史高点锚定"],
  ["LOSS_AVERSION", "不愿确认亏损"],
  ["SOCIAL_IDEA", "社交来源想法"],
] as const;

function list(value?: string | null) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

export function ActionCard({ action }: { action: DashboardAction }) {
  const queryClient = useQueryClient();
  const [reasonTags, setReasonTags] = useState<string[]>([]);
  const acknowledgement = useMutation({
    mutationFn: (decisionType: "HANDLED" | "DEFERRED" | "IGNORED") =>
      postJson(`/api/v1/recommendations/${action.id}/acknowledge`, {
        idempotencyKey: crypto.randomUUID(),
        decisionType,
        rationale: null,
        reasonTags,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["executive-brief-today"] }),
  });
  const reasons = list(action.reasonsJson);
  const risks = list(action.risksJson);
  const changes = list(action.changeConditionsJson);
  const actionCopy = presentAction(action.action);
  const priority = presentPriority(action.priority);
  const confidence = presentConfidence(action.confidence);
  const quantity = formatQuantity(action.quantityMin, action.quantityMax);
  return (
    <li
      className="action-card compact-action-card"
      data-priority={action.priority}
      data-tone={actionCopy.tone}
    >
      <header>
        <div>
          <strong>{action.symbol ?? "组合"}</strong>
          <span>
            {action.companyName ?? "公司名称待确认"} ·{" "}
            {presentClassification(action.classification)}
          </span>
        </div>
        <span className={`priority-pill ${priority.tone}`}>
          {priority.label}
        </span>
      </header>
      <h3>{actionCopy.title}</h3>
      {quantity ? (
        <p className="action-quantity">
          建议：{actionCopy.verb} {quantity}
          {action.estimatedAmount
            ? `，约 ${formatMoney(action.estimatedAmount)}`
            : ""}
        </p>
      ) : (
        <p className="quantity-unavailable">
          暂不提供精确股数：{presentReason(action.riskCalculationReason)}
        </p>
      )}
      <p className="analyst-line">
        {reasons[0] ?? "分析证据尚未形成完整结论。"}
      </p>
      <p className="compact-position-line">
        当前 {formatPercent(action.currentWeight)} → 目标{" "}
        {formatPercent(action.targetWeightMin)}–
        {formatPercent(action.targetWeightMax)}
      </p>
      <details className="action-disclosure">
        <summary>查看原因与风险</summary>
        <div className="action-evidence">
          <section>
            <h4>为什么</h4>
            <ul>
              {(reasons.length ? reasons : ["暂无完整原因证据"])
                .slice(0, 3)
                .map((item) => (
                  <li key={item}>{item}</li>
                ))}
            </ul>
          </section>
          <section>
            <h4>主要风险</h4>
            <ul>
              {(risks.length ? risks : ["暂无完整风险证据"])
                .slice(0, 2)
                .map((item) => (
                  <li key={item}>{item}</li>
                ))}
            </ul>
          </section>
        </div>
        <p>
          <strong>什么情况下改变结论：</strong>
          {changes.length ? changes.join("；") : "暂无明确条件"}
        </p>
        <p>
          <strong>置信度：</strong>
          {confidence.label} — {confidence.detail}
        </p>
        <p>
          <strong>数据更新：</strong>
          {formatDateTime(action.dataAsOf)}；<strong>有效期：</strong>
          {formatDateTime(action.validUntil)}
        </p>
        <fieldset className="decision-reason-tags">
          <legend>记录这次决定的依据（可选）</legend>
          {reasonTagOptions.map(([value, label]) => (
            <label key={value}>
              <input
                type="checkbox"
                checked={reasonTags.includes(value)}
                onChange={(event) => {
                  setReasonTags((current) =>
                    event.target.checked
                      ? [...current, value]
                      : current.filter((tag) => tag !== value),
                  );
                }}
              />
              {label}
            </label>
          ))}
        </fieldset>
      </details>
      <div className="action-buttons">
        {action.positionId ? (
          <a className="report-link" href={`/positions/${action.positionId}`}>
            查看完整分析
          </a>
        ) : null}
        <button
          disabled={acknowledgement.isPending}
          onClick={() => {
            acknowledgement.mutate("HANDLED");
          }}
        >
          我已处理
        </button>
        <button
          disabled={acknowledgement.isPending}
          onClick={() => {
            acknowledgement.mutate("DEFERRED");
          }}
        >
          暂不处理
        </button>
      </div>
      <div className="acknowledgement">
        <small>这里只记录你的决定，不会执行交易。</small>
        {acknowledgement.isSuccess ? (
          <span role="status">处理决定已记录。</span>
        ) : null}
        {acknowledgement.isError ? (
          <span role="alert">记录失败，请重试。</span>
        ) : null}
      </div>
    </li>
  );
}
