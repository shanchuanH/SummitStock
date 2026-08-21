import { useCallback, useEffect, useState } from "react";
import { postJson } from "../http";
import { formatDateTime } from "../presentation/date-format";
import type {
  AnalysisStatus,
  CashflowReconciliation,
  ImportConfirmation,
} from "./types";

const stateMessage: Record<string, { title: string; detail: string }> = {
  STARTING: {
    title: "分析正在启动",
    detail: "后台 Worker 将很快开始处理已保存的持仓。",
  },
  RUNNING: {
    title: "分析正在执行",
    detail: "系统正在收集证据并按策略计算结论。",
  },
  PARTIAL: {
    title: "分析已部分完成",
    detail: "部分外部数据暂不可用；系统不会补造缺失指标。",
  },
  STALLED: {
    title: "分析长时间没有推进",
    detail: "建议操作：重新分析；如果仍停滞，请检查数据源。",
  },
  WORKER_OFFLINE: {
    title: "分析服务没有运行",
    detail: "如果你在本机使用 SummitStock，请启动完整环境：make dev",
  },
  FAILED: {
    title: "分析未能完成",
    detail: "查看失败阶段后重试；已完成的数据不会被伪装成完整结论。",
  },
  BLOCKED: {
    title: "投资建议阶段被策略安全门阻止",
    detail: "已有证据仍保留，但系统不会绕过策略发布门。",
  },
  READY: {
    title: "分析已完成",
    detail: "现在可以查看今日简报和完整持仓分析。",
  },
};

export function ImportResult({
  result,
  onAnother,
}: {
  result: ImportConfirmation;
  onAnother: () => void;
}) {
  const [status, setStatus] = useState<AnalysisStatus>();
  const [requestError, setRequestError] = useState<string>();
  const [cashflow, setCashflow] = useState(result.cashflowReconciliation);
  const [cashflowType, setCashflowType] = useState<
    "EXTERNAL_CASHFLOW" | "INTERNAL_TRADE" | "OTHER"
  >("EXTERNAL_CASHFLOW");
  const [cashflowBusy, setCashflowBusy] = useState(false);
  const [reanalyzing, setReanalyzing] = useState(false);
  const [polledRunId, setPolledRunId] = useState(result.analysisRunId);

  async function confirmCashflow() {
    setCashflowBusy(true);
    try {
      setCashflow(
        await postJson<CashflowReconciliation>(
          `/api/v1/portfolio-imports/${result.batchId}/cashflow-confirmation`,
          { type: cashflowType },
        ),
      );
    } finally {
      setCashflowBusy(false);
    }
  }

  async function reanalyze() {
    setReanalyzing(true);
    try {
      const next = await postJson<{ runId: string }>("/api/v1/analysis/runs", {
        reason: "USER_REFRESH",
      });
      setStatus(undefined);
      setPolledRunId(next.runId);
    } finally {
      setReanalyzing(false);
    }
  }

  const refresh = useCallback(async () => {
    try {
      const response = await fetch(`/api/v1/analysis/status/${polledRunId}`, {
        cache: "no-store",
        credentials: "same-origin",
      });
      if (!response.ok) throw new Error(`HTTP ${String(response.status)}`);
      setStatus((await response.json()) as AnalysisStatus);
      setRequestError(undefined);
    } catch (error) {
      setRequestError(
        error instanceof Error ? error.message : "STATUS_UNAVAILABLE",
      );
    }
  }, [polledRunId]);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => {
      window.clearInterval(timer);
    };
  }, [refresh]);

  const message = status ? stateMessage[status.state] : undefined;
  return (
    <section className="context-card import-result" aria-live="polite">
      <p className="eyebrow">导入已确认</p>
      <h2>{message?.title ?? "正在连接分析服务"}</h2>
      <p>
        {message?.detail ?? "正在读取真实 Worker 状态，不会用模拟进度代替。"}
      </p>
      {cashflow.status === "REQUIRED" ? (
        <section className="cashflow-confirmation" aria-label="现金变化确认">
          <h3>
            检测到现金{Number(cashflow.cashChange) >= 0 ? "增加" : "减少"} ${" "}
            {Math.abs(Number(cashflow.cashChange)).toLocaleString("en-US")}
          </h3>
          <p>持仓数量变化无法完整解释这笔现金变化，请确认来源。</p>
          {(
            [
              ["EXTERNAL_CASHFLOW", "外部入金或提款"],
              ["INTERNAL_TRADE", "买卖持仓产生的现金"],
              ["OTHER", "其他 / 仍需核对"],
            ] as const
          ).map(([value, label]) => (
            <label key={value}>
              <input
                checked={cashflowType === value}
                name="cashflow-type"
                onChange={() => {
                  setCashflowType(value);
                }}
                type="radio"
              />
              {label}
            </label>
          ))}
          <button
            disabled={cashflowBusy}
            onClick={() => void confirmCashflow()}
            type="button"
          >
            确认现金变化
          </button>
          {cashflowType === "OTHER" ? (
            <small>
              选择“其他”会继续保留 NAV 待核对状态，不会生成高置信度回撤。
            </small>
          ) : null}
        </section>
      ) : cashflow.status !== "NONE" &&
        cashflow.status !== "BASELINE_ESTABLISHED" ? (
        <p role="status">现金变化已核对：{cashflow.status}</p>
      ) : null}
      {requestError ? (
        <div className="error-panel" role="alert">
          <strong>无法读取分析状态</strong>
          <span>{requestError}</span>
          <button type="button" onClick={() => void refresh()}>
            重新检查
          </button>
        </div>
      ) : null}
      {status ? (
        <dl>
          <div>
            <dt>Worker</dt>
            <dd>{status.worker.alive ? "在线" : "离线"}</dd>
          </div>
          <div>
            <dt>分析进度</dt>
            <dd>
              {status.progress.completed} / {status.progress.total}
            </dd>
          </div>
          <div>
            <dt>当前阶段</dt>
            <dd>{status.progress.currentStage ?? "—"}</dd>
          </div>
          <div>
            <dt>最后更新时间</dt>
            <dd>{formatDateTime(status.progress.lastProgressAt)}</dd>
          </div>
        </dl>
      ) : null}
      {status?.state === "STALLED" ? (
        <div className="analysis-recovery-actions">
          <button
            disabled={reanalyzing}
            onClick={() => void reanalyze()}
            type="button"
          >
            {reanalyzing ? "正在重新分析…" : "重新分析"}
          </button>
          <a href="/advanced/data-health">检查数据源</a>
        </div>
      ) : null}
      {status?.failure ? (
        <p role="alert">
          {status.failure.failedStage}：{status.failure.errorMessage}
        </p>
      ) : null}
      <ol className="analysis-progress" aria-label="Analysis progress">
        {(status?.stages ?? []).map((stage) => (
          <li key={stage.code}>
            <span>{stage.label}</span>
            <strong>{stage.status}</strong>
          </li>
        ))}
      </ol>
      <div className="import-actions">
        <a href="/">返回今日简报</a>
        <button onClick={onAnother} type="button">
          导入另一份快照
        </button>
      </div>
    </section>
  );
}
