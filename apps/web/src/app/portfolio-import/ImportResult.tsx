import { useCallback, useEffect, useState } from "react";
import type { AnalysisStatus, ImportConfirmation } from "./types";

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
    title: "分析进度暂时停滞",
    detail: "持仓已安全保存，可以重新检查 Worker 和数据提供器。",
  },
  WORKER_OFFLINE: {
    title: "分析服务没有运行",
    detail: "持仓已安全保存，但后台 Worker 当前未连接，因此分析还没有开始。",
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

  const refresh = useCallback(async () => {
    try {
      const response = await fetch(
        `/api/v1/analysis/status/${result.analysisRunId}`,
        {
          cache: "no-store",
          credentials: "same-origin",
        },
      );
      if (!response.ok) throw new Error(`HTTP ${String(response.status)}`);
      setStatus((await response.json()) as AnalysisStatus);
      setRequestError(undefined);
    } catch (error) {
      setRequestError(
        error instanceof Error ? error.message : "STATUS_UNAVAILABLE",
      );
    }
  }, [result.analysisRunId]);

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
        </dl>
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
