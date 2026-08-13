import { useEffect, useState } from "react";
import type { AnalysisStatus, ImportConfirmation } from "./types";

export function ImportResult({
  result,
  onAnother,
}: {
  result: ImportConfirmation;
  onAnother: () => void;
}) {
  const [status, setStatus] = useState<AnalysisStatus>();

  useEffect(() => {
    let active = true;
    async function refresh() {
      const response = await fetch(
        `/api/v1/analysis/status/${result.analysisRunId}`,
        { cache: "no-store", credentials: "same-origin" },
      );
      if (response.ok && active)
        setStatus((await response.json()) as AnalysisStatus);
    }
    void refresh();
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [result.analysisRunId]);

  return (
    <section className="context-card import-result" aria-live="polite">
      <p className="eyebrow">导入已确认</p>
      <h2>分析已开始</h2>
      <p>
        {result.openPositionCount} open positions reconciled,{" "}
        {result.closedPositionCount} positions closed, {result.cashRowCount}{" "}
        cash accounts tracked, and {result.compensationRowCount} unvested
        compensation holdings kept outside liquid assets.
      </p>
      <dl>
        <div>
          <dt>分析状态</dt>
          <dd>{result.analysisState}</dd>
        </div>
        <div>
          <dt>Run</dt>
          <dd>{result.analysisRunId}</dd>
        </div>
      </dl>
      <ol className="analysis-progress" aria-label="Analysis progress">
        {(
          status?.stages ?? [
            {
              code: "HOLDINGS",
              label: "Holdings imported",
              status: "COMPLETE",
            },
            { code: "PRICES", label: "Prices", status: "WAITING" },
            { code: "FINANCIALS", label: "Financial data", status: "WAITING" },
            { code: "VALUATION", label: "Valuation", status: "WAITING" },
            {
              code: "EVENTS",
              label: "Analyst estimates and earnings",
              status: "WAITING",
            },
            {
              code: "PORTFOLIO_RISK",
              label: "Portfolio risk",
              status: "WAITING",
            },
            {
              code: "HOLDING_ANALYSIS",
              label: "Holding analysis",
              status: "WAITING",
            },
            { code: "TODAY_BRIEF", label: "Today's brief", status: "WAITING" },
          ]
        ).map((stage) => (
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
