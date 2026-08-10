import type { components } from "@portfolio/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { postJson } from "../http";

export type DashboardAction = components["schemas"]["BriefAction"];

const actionLabels: Record<string, string> = {
  BUY: "买入", ADD: "增持", HOLD: "持有", TRIM: "减持", SELL: "卖出",
  WATCH: "观察", WAIT_FOR_DATA: "等待数据", DO_NOT_CHASE: "不要追高",
};

function list(value?: string | null) {
  if (!value) return [];
  try { const parsed: unknown = JSON.parse(value); return Array.isArray(parsed) ? parsed.map(String) : []; }
  catch { return []; }
}
function percent(value?: string | null) { return value == null ? "—" : `${(Number(value) * 100).toFixed(1)}%`; }
function quantity(action: DashboardAction) {
  if (!action.quantityMin && !action.quantityMax) return "证据不足，暂不提供精确数量";
  if (action.quantityMin === action.quantityMax) return `${String(action.quantityMin ?? action.quantityMax)} 股`;
  return `${action.quantityMin ?? "—"}–${action.quantityMax ?? "—"} 股`;
}

export function ActionCard({ action }: { action: DashboardAction }) {
  const queryClient = useQueryClient();
  const [rationale, setRationale] = useState("");
  const acknowledgement = useMutation({
    mutationFn: (decisionType: "HANDLED" | "DEFERRED" | "IGNORED") =>
      postJson(`/api/v1/recommendations/${action.id}/acknowledge`, {
        idempotencyKey: crypto.randomUUID(), decisionType, rationale: rationale.trim() || null,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["executive-brief-today"] }),
  });
  const reasons = list(action.reasonsJson);
  const risks = list(action.risksJson);
  const changes = list(action.changeConditionsJson);
  return (
    <li className="action-card" data-priority={action.priority}>
      <header>
        <div><strong>{action.symbol ?? "组合"}</strong><span>{action.classification ?? "分类待确认"}</span></div>
        <div className="action-verdict"><b>{actionLabels[action.action] ?? action.action}</b>{actionLabels[action.action] ? <small>{action.action}</small> : null}</div>
      </header>
      <p className="analyst-line">{reasons[0] ?? "分析证据尚未形成完整结论。"}</p>
      <dl className="action-metrics">
        <div><dt>当前 → 目标</dt><dd>{percent(action.currentWeight)} → {percent(action.targetWeightMin)}–{percent(action.targetWeightMax)}</dd></div>
        <div><dt>建议数量</dt><dd>{quantity(action)}</dd></div>
        <div><dt>置信度</dt><dd>{action.confidence}</dd></div>
        <div><dt>风险变化</dt><dd>{percent(action.riskBeforeFraction)} → {percent(action.riskAfterFraction)}</dd></div>
      </dl>
      <div className="action-evidence">
        <section><h3>为什么</h3><ul>{(reasons.length ? reasons : ["暂无完整原因证据"]).slice(0, 3).map(x => <li key={x}>{x}</li>)}</ul></section>
        <section><h3>主要风险</h3><ul>{(risks.length ? risks : ["暂无完整风险证据"]).slice(0, 2).map(x => <li key={x}>{x}</li>)}</ul></section>
      </div>
      <p><strong>改变条件：</strong>{changes.length ? changes.join("；") : "暂无明确条件"}</p>
      <p><strong>有效期：</strong>{new Date(action.validUntil).toLocaleString("zh-CN")}</p>
      {action.positionId ? <a className="report-link" href={`/positions/${action.positionId}`}>打开完整报告 →</a> : null}
      <details className="acknowledgement"><summary>记录处理结果</summary>
        <label>处理备注（可选）<input value={rationale} onChange={e => { setRationale(e.target.value); }} /></label>
        <div>{(["HANDLED", "DEFERRED", "IGNORED"] as const).map((value, i) => <button key={value} disabled={acknowledgement.isPending} onClick={() => { acknowledgement.mutate(value); }}>{["已处理", "暂缓", "忽略本次"][i]}</button>)}</div>
        <small>记录确认不等于执行交易。</small>
        {acknowledgement.isSuccess ? <span role="status">处理决定已记录。</span> : null}
        {acknowledgement.isError ? <span role="alert">记录失败，请重试。</span> : null}
      </details>
    </li>
  );
}
