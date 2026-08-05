import { api, type components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";

async function requireData<T>(
  request: Promise<{ data?: T; error?: unknown; response: Response }>,
) {
  const { data, error, response } = await request;
  if (data === undefined)
    throw new Error(
      `API request failed (${String(response.status)}): ${JSON.stringify(error)}`,
    );
  return data;
}

export function PortfolioPage() {
  const summary = useQuery({
    queryKey: ["portfolio-summary"],
    queryFn: () => requireData(api.GET("/api/v1/portfolio/summary")),
    retry: false,
  });
  const positions = useQuery({
    queryKey: ["positions"],
    queryFn: () => requireData(api.GET("/api/v1/positions")),
    retry: false,
  });
  const actions = useQuery({
    queryKey: ["today-actions"],
    queryFn: () => requireData(api.GET("/api/v1/actions/today")),
    retry: false,
  });

  if (summary.isError || positions.isError || actions.isError) {
    return (
      <main className="inspection-shell">
        <a href="/" className="back-link">
          <ArrowLeft aria-hidden="true" /> Home
        </a>
        <aside className="error portfolio-auth" role="alert">
          <strong>Sign in required</strong>
          <span>
            Portfolio holdings, classifications, and actions are private.
          </span>
        </aside>
      </main>
    );
  }

  return (
    <main className="inspection-shell portfolio-shell">
      <header className="inspection-header">
        <a href="/market-context" className="back-link">
          <ArrowLeft aria-hidden="true" /> Market context
        </a>
        <div>
          <p className="eyebrow">PACKET 04 / HOLDING INTELLIGENCE</p>
          <h1>Portfolio, without guesswork.</h1>
        </div>
        <span className="health-pill">
          {summary.isPending ? "LOADING" : "PRIVATE"}
        </span>
      </header>
      <section className="portfolio-metrics" aria-label="Portfolio summary">
        <article>
          <span>Invested</span>
          <strong>{summary.data?.investedValue ?? "—"}</strong>
        </article>
        <article>
          <span>Tracked cash</span>
          <strong>{summary.data?.trackedCash ?? "—"}</strong>
        </article>
        <article>
          <span>Open positions</span>
          <strong>{summary.data?.openPositions ?? "—"}</strong>
        </article>
        <article>
          <span>Data as of</span>
          <strong>{summary.data?.dataAsOf ?? "—"}</strong>
        </article>
      </section>
      <section className="portfolio-layout">
        <a className="market-link" href="/dip-buy">
          Open ETF Dip & cashflow workspace →
        </a>
        <article className="context-card portfolio-positions">
          <p className="eyebrow">POSITIONS / CORE + TACTICAL OVERLAY</p>
          {(positions.data ?? []).length === 0 ? (
            <p className="empty">No positions imported.</p>
          ) : null}
          {(positions.data ?? []).map((position) => (
            <div className="position-row" key={position.id}>
              <strong>
                <a href={`/positions/${position.id ?? ""}`}>
                  {position.symbol}
                </a>
              </strong>
              <span>{position.bucket}</span>
              <span>{position.classification}</span>
              <span>{position.marketValue}</span>
              <small>
                {position.classificationConfirmed
                  ? "CONFIRMED"
                  : "CONFIRM REQUIRED"}
              </small>
            </div>
          ))}
        </article>
        <article className="context-card today-actions">
          <p className="eyebrow">TODAY'S ACTIONS</p>
          <ActionGroup title="MUST ACT" items={actions.data?.mustAct ?? []} />
          <ActionGroup title="DO NOT" items={actions.data?.doNot ?? []} />
          <ActionGroup title="WATCH" items={actions.data?.watch ?? []} />
          {!actions.isPending &&
          !(
            actions.data.mustAct?.length ||
            actions.data.doNot?.length ||
            actions.data.watch?.length
          ) ? (
            <p className="empty">
              Analysis readiness has not been confirmed. No action conclusion is
              available yet.
            </p>
          ) : null}
        </article>
        <article className="context-card">
          <p className="eyebrow">CLASSIFICATION AND TRADE PLANS</p>
          <p className="quality-policy">
            This functionality is being migrated to server-side calculations
            based on the selected position and the real portfolio. It is not
            currently available.
          </p>
        </article>
      </section>
      <footer>
        <span>NO AUTO TRADING</span>
        <span>MAX 3 MUST-ACT ITEMS</span>
        <span>DECIMALS PRESERVED AS STRINGS</span>
      </footer>
    </main>
  );
}

function ActionGroup({
  title,
  items = [],
}: {
  title: string;
  items?: components["schemas"]["RecommendationResponse"][];
}) {
  return (
    <section className="action-group">
      <h2>{title}</h2>
      {items.map((item) => (
        <p key={item.id}>
          <strong>{item.symbol ?? "PORTFOLIO"}</strong> {item.action}
        </p>
      ))}
    </section>
  );
}
