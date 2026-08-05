import { api } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { Activity, ArrowLeft, Database, Search } from "lucide-react";
import { useState } from "react";
import type { SyntheticEvent } from "react";

async function requireData<T>(
  request: Promise<{ data?: T; error?: unknown; response: Response }>,
) {
  const { data, error, response } = await request;
  if (data === undefined) {
    throw new Error(
      `API request failed (${String(response.status)}): ${JSON.stringify(error)}`,
    );
  }
  return data;
}

function useMarketData(symbol: string) {
  const health = useQuery({
    queryKey: ["market-health"],
    queryFn: () => requireData(api.GET("/api/v1/market/data-health")),
    refetchInterval: 60_000,
  });
  const instruments = useQuery({
    queryKey: ["instruments"],
    queryFn: () =>
      requireData(
        api.GET("/api/v1/instruments", { params: { query: { limit: 50 } } }),
      ),
  });
  const series = useQuery({
    queryKey: ["market-series", symbol],
    queryFn: async () => {
      const [bars, indicators, fundamentals] = await Promise.all([
        requireData(
          api.GET("/api/v1/instruments/{symbol}/bars", {
            params: { path: { symbol }, query: { adjusted: true, limit: 30 } },
          }),
        ),
        requireData(
          api.GET("/api/v1/instruments/{symbol}/indicators", {
            params: { path: { symbol }, query: { limit: 50 } },
          }),
        ),
        requireData(
          api.GET("/api/v1/instruments/{symbol}/fundamentals", {
            params: { path: { symbol }, query: { limit: 30 } },
          }),
        ),
      ]);
      return { bars, indicators, fundamentals };
    },
  });
  return { health, instruments, series };
}

export function MarketInspectionPage() {
  const [symbol, setSymbol] = useState("SPY");
  const [draft, setDraft] = useState("SPY");
  const { health, instruments, series } = useMarketData(symbol);

  function inspect(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = draft.trim().toUpperCase();
    if (normalized) setSymbol(normalized);
  }

  const hasError = health.isError || instruments.isError || series.isError;
  return (
    <main className="inspection-shell">
      <header className="inspection-header">
        <nav className="inspection-nav">
          <a href="/" className="back-link">
            <ArrowLeft aria-hidden="true" /> Foundation
          </a>
          <a href="/market-context">Market context →</a>
        </nav>
        <div>
          <p className="eyebrow">PACKET 02 / READ-ONLY INSPECTION</p>
          <h1>Market data ledger.</h1>
        </div>
        <span
          className={`health-pill health-pill--${health.data?.status?.toLowerCase() ?? "loading"}`}
        >
          <Activity aria-hidden="true" /> {health.data?.status ?? "LOADING"}
        </span>
      </header>

      <section className="inspection-controls">
        <form onSubmit={inspect}>
          <label htmlFor="symbol">Instrument symbol</label>
          <div>
            <input
              id="symbol"
              value={draft}
              onChange={(event) => {
                setDraft(event.target.value);
              }}
              list="known-instruments"
              autoComplete="off"
            />
            <datalist id="known-instruments">
              {instruments.data?.items?.map((instrument) => (
                <option key={instrument.id} value={instrument.symbol} />
              ))}
            </datalist>
            <button type="submit">
              <Search aria-hidden="true" /> Inspect
            </button>
          </div>
        </form>
        <dl className="health-metrics">
          <div>
            <dt>Instruments</dt>
            <dd>{health.data?.activeInstruments ?? "—"}</dd>
          </div>
          <div>
            <dt>Price bars</dt>
            <dd>{health.data?.priceBars ?? "—"}</dd>
          </div>
          <div>
            <dt>Snapshots</dt>
            <dd>{health.data?.indicatorSnapshots ?? "—"}</dd>
          </div>
          <div>
            <dt>Open quality events</dt>
            <dd>{health.data?.openQualityEvents ?? "—"}</dd>
          </div>
        </dl>
      </section>

      {hasError ? (
        <aside className="error" role="alert">
          <strong>Market data unavailable</strong>
          <span>
            The inspection view does not substitute or fabricate missing values.
          </span>
        </aside>
      ) : null}

      <section className="inspection-grid" aria-busy={series.isPending}>
        <article className="inspection-panel inspection-panel--wide">
          <h2>
            <Database aria-hidden="true" /> {symbol} adjusted daily bars
          </h2>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Open</th>
                  <th>High</th>
                  <th>Low</th>
                  <th>Close</th>
                  <th>Quality</th>
                </tr>
              </thead>
              <tbody>
                {series.data?.bars.items?.map((bar) => (
                  <tr key={bar.marketDate}>
                    <td>{bar.marketDate}</td>
                    <td>{bar.open}</td>
                    <td>{bar.high}</td>
                    <td>{bar.low}</td>
                    <td>{bar.close}</td>
                    <td>{bar.qualityStatus}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!series.isPending && !series.data?.bars.items?.length ? (
            <p className="empty">No price bars.</p>
          ) : null}
        </article>

        <article className="inspection-panel">
          <h2>Indicator snapshots</h2>
          <ul className="snapshot-list">
            {series.data?.indicators.items?.map((indicator, index) => (
              <li
                key={`${indicator.marketDate ?? "unknown"}-${indicator.indicatorCode ?? "unknown"}-${String(index)}`}
              >
                <span>
                  {indicator.indicatorCode}
                  <small>{indicator.marketDate}</small>
                </span>
                <strong>
                  {indicator.value?.toFixed(4) ?? indicator.status ?? "—"}
                </strong>
              </li>
            ))}
          </ul>
          {!series.isPending && !series.data?.indicators.items?.length ? (
            <p className="empty">No indicator snapshots.</p>
          ) : null}
        </article>

        <article className="inspection-panel">
          <h2>Fundamental observations</h2>
          <ul className="snapshot-list">
            {series.data?.fundamentals.items?.map((item, index) => (
              <li
                key={`${item.metricCode ?? "unknown"}-${item.periodEnd ?? "unknown"}-${String(index)}`}
              >
                <span>
                  {item.metricCode}
                  <small>{item.periodEnd}</small>
                </span>
                <strong>
                  {item.value ?? item.text ?? "—"} {item.unit}
                </strong>
              </li>
            ))}
          </ul>
          {!series.isPending && !series.data?.fundamentals.items?.length ? (
            <p className="empty">No fundamental observations.</p>
          ) : null}
        </article>
      </section>

      <footer>
        <span>RAW + ADJUSTED KEPT SEPARATELY</span>
        <span>UTC / EXCHANGE MARKET DATE</span>
        <span>DATA AS OF {health.data?.dataAsOf ?? "—"}</span>
      </footer>
    </main>
  );
}
