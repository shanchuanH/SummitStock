import { api } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ArrowLeft } from "lucide-react";
import { useParams } from "react-router";

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

function jsonArray(value?: string) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

export function PositionDetailPage() {
  const { positionId = "" } = useParams();
  const position = useQuery({
    queryKey: ["position", positionId],
    queryFn: () =>
      requireData(
        api.GET("/api/v1/positions/{id}", {
          params: { path: { id: positionId } },
        }),
      ),
    enabled: positionId !== "",
    retry: false,
  });
  const intelligence = useQuery({
    queryKey: ["position-intelligence", positionId],
    queryFn: () =>
      requireData(
        api.GET("/api/v1/positions/{positionId}/intelligence", {
          params: { path: { positionId } },
        }),
      ),
    enabled: positionId !== "",
    retry: false,
  });

  if (position.isError || intelligence.isError)
    return (
      <main className="inspection-shell">
        <a className="back-link" href="/portfolio">
          <ArrowLeft aria-hidden="true" /> Portfolio
        </a>
        <aside className="error portfolio-auth" role="alert">
          <strong>Position unavailable</strong>
          <span>
            Sign in or verify that this position belongs to your account.
          </span>
        </aside>
      </main>
    );
  const data = intelligence.data;
  const stale =
    data?.stop?.qualityStatus && data.stop.qualityStatus !== "HEALTHY";
  return (
    <main className="inspection-shell position-detail-shell">
      <header className="inspection-header">
        <a className="back-link" href="/portfolio">
          <ArrowLeft aria-hidden="true" /> Portfolio
        </a>
        <div>
          <p className="eyebrow">PACKET 05 / POSITION INTELLIGENCE</p>
          <h1>{position.data?.symbol ?? "Position"}</h1>
        </div>
        <span className="health-pill">
          {position.data?.classification ?? "LOADING"}
        </span>
      </header>
      {stale ? (
        <aside className="quality-warning" role="alert">
          <AlertTriangle aria-hidden="true" />
          <span>
            <strong>Stale stop evidence</strong> Review only; no precise
            execution quantity.
          </span>
        </aside>
      ) : null}
      <section className="position-intelligence-grid">
        <article className="context-card position-chart">
          <p className="eyebrow">PRICE / ENTRY / STOP / EARNINGS</p>
          <div
            className="chart-placeholder"
            aria-label="Position chart scaffold"
          >
            <span>Server-owned chart series</span>
            {data?.stop ? (
              <>
                <i className="entry-line">ENTRY {data.stop.entryPrice}</i>
                <i className="stop-line">LIVE STOP {data.stop.liveStop}</i>
              </>
            ) : (
              <p className="empty">No stop series yet.</p>
            )}
          </div>
        </article>
        <article className="context-card">
          <p className="eyebrow">STOPS</p>
          {data?.stop ? (
            <dl className="score-breakdown">
              <div>
                <dt>Initial</dt>
                <dd>{data.stop.initialStop}</dd>
              </div>
              <div>
                <dt>Live</dt>
                <dd>{data.stop.liveStop}</dd>
              </div>
              <div>
                <dt>Soft alert</dt>
                <dd>{data.stop.softAlert}</dd>
              </div>
              <div>
                <dt>Catastrophic</dt>
                <dd>{data.stop.catastrophicStop}</dd>
              </div>
            </dl>
          ) : (
            <p className="empty">
              Core ETFs or missing EOD evidence may have no ordinary stock stop.
            </p>
          )}
        </article>
        <article className="context-card">
          <p className="eyebrow">THESIS</p>
          {data?.thesis ? (
            <>
              <h2>{data.thesis.status}</h2>
              <p>{data.thesis.summary}</p>
              <small>
                {data.thesis.userConfirmed
                  ? "USER CONFIRMED"
                  : "CONFIRMATION REQUIRED"}{" "}
                · expires {data.thesis.expiresAt}
              </small>
              <ul>
                {jsonArray(data.thesis.sourcesJson).map((source) => (
                  <li key={source}>{source}</li>
                ))}
              </ul>
            </>
          ) : (
            <p className="empty">
              No structured thesis. LLM summaries cannot create a status.
            </p>
          )}
        </article>
        <article className="context-card">
          <p className="eyebrow">QUALITY / VALUATION</p>
          {data?.valuation ? (
            <>
              <h2>{data.valuation.action}</h2>
              <p>
                Fundamentals {data.valuation.fundamentalHealth} · revisions{" "}
                {data.valuation.earningsRevisions} · stabilization{" "}
                {data.valuation.priceStabilization}
              </p>
              <small>
                Discount tactical sleeve {data.valuation.discountTacticalWeight}
              </small>
            </>
          ) : (
            <p className="empty">No valuation evidence.</p>
          )}
        </article>
        <article className="context-card">
          <p className="eyebrow">EARNINGS RISK</p>
          {data?.earnings ? (
            <>
              <h2>{data.earnings.action}</h2>
              <p>
                {data.earnings.eventCount} events · P90 gap{" "}
                {data.earnings.gapP90Fraction} · cushion{" "}
                {data.earnings.profitCushionR}R
              </p>
              <small>Next event {data.earnings.nextEventAt ?? "unknown"}</small>
            </>
          ) : (
            <p className="empty">8–12 historical events required.</p>
          )}
        </article>
        <article className="context-card">
          <p className="eyebrow">TAX / JOURNAL</p>
          {(data?.journal ?? []).length ? (
            (data?.journal ?? []).map((item) => (
              <div className="journal-row" key={item.id}>
                <strong>{item.entryType}</strong>
                <span>{item.taxStatus}</span>
                <span>
                  Realized {item.realizedR ?? "—"}R · MFE {item.mfeR ?? "—"}R ·
                  MAE {item.maeR ?? "—"}R
                </span>
                <small>{item.exitReason ?? "OPEN"}</small>
              </div>
            ))
          ) : (
            <p className="empty">No journal entries.</p>
          )}
        </article>
      </section>
      <footer>
        <span>FORMAL STOPS AFTER COMPLETED DAILY BAR</span>
        <span>RISK BEFORE TAX</span>
        <span>NO AUTO TRADING</span>
      </footer>
    </main>
  );
}
