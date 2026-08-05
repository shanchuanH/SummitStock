import { api, type components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { ActionCard } from "./ActionCard";
import { DataReadinessBanner } from "./DataReadinessBanner";
import { EmptyPortfolioState } from "./EmptyPortfolioState";
import { PortfolioHealthCard } from "./PortfolioHealthCard";
import { WorkspaceNav } from "../workspace-nav";

type ExecutiveBrief = components["schemas"]["ExecutiveBrief"];

class BriefRequestError extends Error {
  constructor(readonly status: number) {
    super(`Executive brief unavailable (${String(status)})`);
  }
}

async function getExecutiveBrief(): Promise<ExecutiveBrief> {
  const { data, response } = await api.GET("/api/v1/brief/today");
  if (!data) throw new BriefRequestError(response.status);
  return data;
}

function SummaryCard({ brief }: { brief: ExecutiveBrief }) {
  return (
    <article className="context-card">
      <p className="eyebrow">PORTFOLIO SUMMARY</p>
      <h2>{brief.summary.openPositions} open positions</h2>
      <dl className="health-metrics">
        <div>
          <dt>Invested value</dt>
          <dd>${brief.summary.investedValue}</dd>
        </div>
        <div>
          <dt>Tracked cash</dt>
          <dd>${brief.summary.trackedCash}</dd>
        </div>
        <div>
          <dt>Emergency cash</dt>
          <dd>${brief.summary.emergencyCash}</dd>
        </div>
        <div>
          <dt>Tactical reserve</dt>
          <dd>${brief.summary.tacticalReserve}</dd>
        </div>
      </dl>
    </article>
  );
}

export function ExecutiveDashboardPage() {
  const brief = useQuery({
    queryKey: ["executive-brief-today"],
    queryFn: getExecutiveBrief,
    retry: false,
  });

  return (
    <main className="shell workspace-shell">
      <WorkspaceNav />
      <section className="workspace-heading">
        <p className="eyebrow">DAILY CONTROL SURFACE</p>
        <h1>Dashboard</h1>
      </section>
      {brief.isPending ? (
        <section className="context-card">
          <p className="empty-state">Loading the executive brief…</p>
        </section>
      ) : brief.isError ? (
        <section className="context-card" role="alert">
          <h2>
            {brief.error instanceof BriefRequestError &&
            brief.error.status === 401
              ? "Sign in required"
              : "Executive brief unavailable"}
          </h2>
          <p>
            {brief.error instanceof BriefRequestError &&
            brief.error.status === 401
              ? "Sign in from Settings to view private portfolio decisions."
              : "The analysis status is unknown. No action conclusion is available."}
          </p>
        </section>
      ) : (
        <>
          <DataReadinessBanner
            state={brief.data.state}
            headline={brief.data.headline}
            readiness={brief.data.dataReadiness}
          />
          <section className="dashboard-grid">
            {brief.data.state === "NO_PORTFOLIO" ? (
              <EmptyPortfolioState />
            ) : (
              <article className="context-card dashboard-actions">
                <h2>Priority actions</h2>
                {brief.data.state === "ANALYSIS_READY" &&
                brief.data.mustAct.length === 0 &&
                brief.data.doNot.length === 0 &&
                brief.data.watch.length === 0 ? (
                  <div className="calm-state">
                    <strong>NO URGENT ACTION</strong>
                    <span>
                      Analysis is complete and no action queue is active.
                    </span>
                  </div>
                ) : brief.data.mustAct.length > 0 ? (
                  <ol className="action-list">
                    {brief.data.mustAct.slice(0, 3).map((action) => (
                      <ActionCard key={action.id} action={action} />
                    ))}
                  </ol>
                ) : (
                  <p className="empty-state">{brief.data.headline}</p>
                )}
              </article>
            )}
            <SummaryCard brief={brief.data} />
            <PortfolioHealthCard health={brief.data.portfolioHealth} />
          </section>
          <section className="context-card">
            <p className="eyebrow">EVIDENCE</p>
            <p>
              Strategy {brief.data.strategyVersion ?? "not selected"} · data as
              of {brief.data.dataAsOf ?? "not available"}
            </p>
          </section>
        </>
      )}
      <footer>
        <span>DECISION SUPPORT ONLY</span>
        <span>ACKNOWLEDGEMENT IS NOT EXECUTION</span>
        <span>NO AUTO TRADING</span>
      </footer>
    </main>
  );
}
