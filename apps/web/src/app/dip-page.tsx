import { api } from "@portfolio/api-client";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowLeft, ShieldCheck } from "lucide-react";

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

export function DipPage() {
  const status = useQuery({
    queryKey: ["dip-status"],
    queryFn: () => requireData(api.GET("/api/v1/etf-dip/status")),
    retry: false,
  });
  const history = useQuery({
    queryKey: ["recommendation-history"],
    queryFn: () => requireData(api.GET("/api/v1/recommendations/history")),
    retry: false,
  });
  const cashflow = useMutation({
    mutationFn: async () => {
      const csrf = await requireData(api.GET("/api/v1/auth/csrf", {}));
      return requireData(
        api.POST("/api/v1/cashflow/plan", {
          headers: { [csrf.headerName ?? "X-CSRF-TOKEN"]: csrf.token ?? "" },
          body: {
            monthlyTakeHome: 11000,
            monthlyExpenses: 4000,
            emergencyCash: 20000,
            qualitySignal: false,
          },
        }),
      );
    },
  });
  if (status.isError || history.isError)
    return (
      <main className="inspection-shell">
        <a href="/portfolio" className="back-link">
          <ArrowLeft aria-hidden="true" /> Portfolio
        </a>
        <aside className="error portfolio-auth" role="alert">
          <strong>Sign in required</strong>
          <span>Dip events and cash allocation are private.</span>
        </aside>
      </main>
    );
  const event = status.data?.event;
  return (
    <main className="inspection-shell dip-shell">
      <header className="inspection-header">
        <a href="/portfolio" className="back-link">
          <ArrowLeft aria-hidden="true" /> Portfolio
        </a>
        <div>
          <p className="eyebrow">PACKET 06 / ETF DIP & CASHFLOW</p>
          <h1>Wait for confirmation.</h1>
        </div>
        <span className="health-pill">
          {event?.status ?? status.data?.status ?? "LOADING"}
        </span>
      </header>
      <section className="dip-grid">
        <article className="context-card dip-score">
          <p className="eyebrow">DISLOCATION / SETUP</p>
          {event ? (
            <>
              <strong>{event.setupScore?.toFixed(1)}</strong>
              <span>/ 100 · {event.triggerCount} triggers</span>
              <p>
                {event.symbol} · drawdown {event.portfolioDrawdown} ·{" "}
                {event.marketDriven ? "MARKET DRIVEN" : "NOT MARKET DRIVEN"}
              </p>
            </>
          ) : (
            <p className="empty">No active event. NO ACTION is normal.</p>
          )}
        </article>
        <article className="context-card">
          <p className="eyebrow">FOUR TRANCHES</p>
          <div className="tranche-row">
            {["20%", "25%", "30%", "25%"].map((v, i) => (
              <span key={`${v}-${String(i)}`}>
                <b>{i + 1}</b>
                {v}
              </span>
            ))}
          </div>
          <p className="quality-policy">
            One use per level · five trading-day cooldown · manual confirmation
            only.
          </p>
        </article>
        <article className="context-card">
          <p className="eyebrow">MONTHLY CASHFLOW</p>
          <p>
            $11,000 take-home − $4,000 expenses = $7,000 sustainable surplus.
          </p>
          <button
            type="button"
            onClick={() => {
              cashflow.mutate();
            }}
          >
            Preview no-signal allocation
          </button>
          {cashflow.data ? (
            <dl className="score-breakdown">
              <div>
                <dt>Broad Core</dt>
                <dd>{cashflow.data.broadCore}</dd>
              </div>
              <div>
                <dt>Tech Core</dt>
                <dd>{cashflow.data.techCore}</dd>
              </div>
              <div>
                <dt>International</dt>
                <dd>{cashflow.data.internationalCore}</dd>
              </div>
              <div>
                <dt>Tactical reserve</dt>
                <dd>{cashflow.data.tacticalReserve}</dd>
              </div>
            </dl>
          ) : null}
        </article>
        <article className="context-card">
          <p className="eyebrow">RECOMMENDATION HISTORY</p>
          {(history.data ?? []).length ? (
            (history.data ?? []).map((r, index) => (
              <p
                className="history-row"
                key={`${r.action ?? "action"}-${String(index)}`}
              >
                <ShieldCheck aria-hidden="true" />
                <strong>{r.symbol ?? "PORTFOLIO"}</strong>
                <span>
                  {r.action} · {r.confidence}
                </span>
                <small>{r.status}</small>
              </p>
            ))
          ) : (
            <p className="empty">No recommendation history.</p>
          )}
        </article>
      </section>
      <footer>
        <span>EMERGENCY CASH EXCLUDED</span>
        <span>RECOVERY SELLS TACTICAL ONLY</span>
        <span>NO EXECUTION</span>
      </footer>
    </main>
  );
}
