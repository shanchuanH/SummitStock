import { api } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { FlaskConical } from "lucide-react";
import { WorkspaceNav } from "./workspace-nav";

async function latestReport() {
  const { data, response } = await api.GET("/api/v1/backtests/latest");
  if (!data)
    throw new Error(
      response.status === 404
        ? "NO_REPORT"
        : `REPORT_UNAVAILABLE:${String(response.status)}`,
    );
  return data;
}

export function BacktestPage() {
  const report = useQuery({
    queryKey: ["backtest-latest"],
    queryFn: latestReport,
    retry: false,
  });
  return (
    <main className="shell workspace-shell">
      <WorkspaceNav />
      <section className="workspace-heading">
        <p className="eyebrow">REPLAY / OUT-OF-SAMPLE</p>
        <h1>Backtest</h1>
      </section>
      {report.isPending ? (
        <section className="context-card concise-card">
          <strong>LOADING VERIFIED REPORT</strong>
        </section>
      ) : report.isError ? (
        <section className="context-card concise-card">
          <FlaskConical aria-hidden="true" />
          <h2>NO VERIFIED REPORT</h2>
          <p>
            Run the offline replay against a complete point-in-time universe.
            The UI will not invent metrics.
          </p>
        </section>
      ) : (
        <>
          <section className="portfolio-metrics">
            <article>
              <span>Strategy</span>
              <strong>{report.data.strategyVersion}</strong>
            </article>
            <article>
              <span>Period</span>
              <strong>
                {report.data.periodStart} — {report.data.periodEnd}
              </strong>
            </article>
            <article>
              <span>Bias review</span>
              <strong>{report.data.biasStatus}</strong>
            </article>
            <article>
              <span>OOS begins</span>
              <strong>{report.data.outOfSampleFrom ?? "NOT SET"}</strong>
            </article>
          </section>
          <section className="context-card backtest-metrics">
            <h2>Decision metrics</h2>
            {(report.data.metrics ?? []).map((metric) => (
              <article
                key={`${metric.name ?? "metric"}:${String(metric.horizonDays ?? -1)}:${metric.sleeve ?? "ALL"}`}
              >
                <span>
                  {metric.name}
                  {metric.horizonDays !== -1
                    ? ` / ${String(metric.horizonDays)}D`
                    : ""}
                </span>
                <strong>{(metric.value ?? 0).toFixed(4)}</strong>
                <small>
                  n={String(metric.sampleCount ?? 0)} · {metric.sleeve}
                </small>
              </article>
            ))}
          </section>
        </>
      )}
      <footer>
        <span>COMPLETED BARS ONLY</span>
        <span>NEXT-OPEN FILLS</span>
        <span>NO AUTO TRADING</span>
      </footer>
    </main>
  );
}
