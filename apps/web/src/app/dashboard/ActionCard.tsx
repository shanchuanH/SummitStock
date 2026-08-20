import type { components } from "@portfolio/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
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

export type DashboardAction = components["schemas"]["BriefAction"];

function list(value?: string | null) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}
function quantity(action: DashboardAction) {
  return (
    formatQuantity(action.quantityMin, action.quantityMax) ??
    "证据不足，暂不提供精确数量"
  );
}

export function ActionCard({ action }: { action: DashboardAction }) {
  const queryClient = useQueryClient();
  const acknowledgement = useMutation({
    mutationFn: (decisionType: "HANDLED" | "DEFERRED" | "IGNORED") =>
      postJson(`/api/v1/recommendations/${action.id}/acknowledge`, {
        idempotencyKey: crypto.randomUUID(),
        decisionType,
        rationale: null,
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
  return (
    <li className="action-card" data-priority={action.priority}>
      <header>
        <div>
          <strong>{action.symbol ?? "组合"}</strong>
          <span>
            {action.companyName ?? "公司名称待确认"} ·{" "}
            {presentClassification(action.classification)}
          </span>
        </div>
        <div className="action-verdict">
          <b>{actionCopy.title}</b>
        </div>
      </header>
      <p className="analyst-line">
        {reasons[0] ?? "分析证据尚未形成完整结论。"}
      </p>
      <dl className="action-metrics">
        <div>
          <dt>当前 → 目标</dt>
          <dd>
            {formatPercent(action.currentWeight)} →{" "}
            {formatPercent(action.targetWeightMin)}–
            {formatPercent(action.targetWeightMax)}
          </dd>
        </div>
        <div>
          <dt>建议数量</dt>
          <dd>{quantity(action)}</dd>
        </div>
        <div>
          <dt>建议金额</dt>
          <dd>{formatMoney(action.estimatedAmount)}</dd>
        </div>
        <div>
          <dt>优先级 / 置信度</dt>
          <dd>
            {priority.label} / {confidence.label}
          </dd>
        </div>
      </dl>
      <div className="action-evidence">
        <section>
          <h3>为什么</h3>
          <ul>
            {(reasons.length ? reasons : ["暂无完整原因证据"])
              .slice(0, 3)
              .map((x) => (
                <li key={x}>{x}</li>
              ))}
          </ul>
        </section>
        <section>
          <h3>主要风险</h3>
          <ul>
            {(risks.length ? risks : ["暂无完整风险证据"])
              .slice(0, 2)
              .map((x) => (
                <li key={x}>{x}</li>
              ))}
          </ul>
        </section>
      </div>
      <p>
        <strong>改变条件：</strong>
        {changes.length ? changes.join("；") : "暂无明确条件"}
      </p>
      <p>
        <strong>数据更新：</strong>
        {formatDateTime(action.dataAsOf)}；<strong>有效期：</strong>
        {formatDateTime(action.validUntil)}
      </p>
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
        <small>记录确认不等于执行交易。</small>
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
