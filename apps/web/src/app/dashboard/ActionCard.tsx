import type { components } from "@portfolio/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { postJson } from "../http";

type ExtendedFields = {
  classification?: string | null;
  quantityMin?: string | null;
  quantityMax?: string | null;
  currentWeight?: string | null;
  targetWeightMin?: string | null;
  targetWeightMax?: string | null;
  estimatedAmount?: string | null;
  riskBeforeFraction?: string | null;
  riskAfterFraction?: string | null;
  reasonsJson?: string | null;
  risksJson?: string | null;
  changeConditionsJson?: string | null;
  validUntil?: string | null;
};

export type DashboardAction = components["schemas"]["BriefAction"] &
  ExtendedFields;

function list(value?: string | null) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function percent(value?: string | null) {
  return value == null ? "—" : `${(Number(value) * 100).toFixed(1)}%`;
}

function quantity(action: DashboardAction) {
  if (!action.quantityMin && !action.quantityMax) return "当前不提供精确数量";
  if (action.quantityMin === action.quantityMax)
    return `${action.quantityMin ?? action.quantityMax ?? "—"} 股`;
  return `${action.quantityMin ?? "—"}–${action.quantityMax ?? "—"} 股`;
}

export function ActionCard({ action }: { action: DashboardAction }) {
  const queryClient = useQueryClient();
  const [rationale, setRationale] = useState("");
  const acknowledgement = useMutation({
    mutationFn: (decisionType: "HANDLED" | "DEFERRED" | "IGNORED") =>
      postJson(`/api/v1/recommendations/${action.id}/acknowledge`, {
        idempotencyKey: crypto.randomUUID(),
        decisionType,
        rationale: rationale.trim() || null,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["executive-brief-today"] }),
  });
  const reasons = list(action.reasonsJson).slice(0, 3);
  const risks = list(action.risksJson).slice(0, 2);
  const changes = list(action.changeConditionsJson);
  return (
    <li className="action-card">
      <header>
        <div>
          <strong>{action.symbol ?? "组合"}</strong>
          <span>{action.classification ?? "待确认分类"}</span>
        </div>
        <b>{action.action}</b>
      </header>
      <dl className="action-metrics">
        <div>
          <dt>建议数量</dt>
          <dd>{quantity(action)}</dd>
        </div>
        <div>
          <dt>当前权重</dt>
          <dd>{percent(action.currentWeight)}</dd>
        </div>
        <div>
          <dt>目标权重</dt>
          <dd>
            {percent(action.targetWeightMin)}–{percent(action.targetWeightMax)}
          </dd>
        </div>
        <div>
          <dt>预计金额</dt>
          <dd>{action.estimatedAmount ? `$${action.estimatedAmount}` : "—"}</dd>
        </div>
        <div>
          <dt>风险变化</dt>
          <dd>
            {percent(action.riskBeforeFraction)} →{" "}
            {percent(action.riskAfterFraction)}
          </dd>
        </div>
        <div>
          <dt>置信度</dt>
          <dd>{action.confidence}</dd>
        </div>
      </dl>
      <div className="action-evidence">
        <section>
          <h3>主要原因</h3>
          {reasons.length ? (
            <ul>
              {reasons.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
          ) : (
            <p>暂无完整原因证据。</p>
          )}
        </section>
        <section>
          <h3>主要风险</h3>
          {risks.length ? (
            <ul>
              {risks.map((risk) => (
                <li key={risk}>{risk}</li>
              ))}
            </ul>
          ) : (
            <p>暂无完整风险证据。</p>
          )}
        </section>
      </div>
      <p>
        <strong>有效期：</strong>
        {action.validUntil
          ? new Date(action.validUntil).toLocaleString("zh-CN")
          : "等待有效期数据"}
      </p>
      <p>
        <strong>改变建议的条件：</strong>
        {changes.length ? changes.join("；") : "暂无明确条件"}
      </p>
      <div className="acknowledgement">
        <label>
          处理备注（可选）
          <input
            value={rationale}
            onChange={(event) => { setRationale(event.target.value); }}
          />
        </label>
        <div>
          <button
            disabled={acknowledgement.isPending}
            onClick={() => { acknowledgement.mutate("HANDLED"); }}
          >
            我已处理
          </button>
          <button
            disabled={acknowledgement.isPending}
            onClick={() => { acknowledgement.mutate("DEFERRED"); }}
          >
            暂不处理
          </button>
          <button
            disabled={acknowledgement.isPending}
            onClick={() => { acknowledgement.mutate("IGNORED"); }}
          >
            忽略本次
          </button>
        </div>
        <small>这不会在券商账户执行交易。</small>
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
