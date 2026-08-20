import { api } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ArrowLeft, Gauge, ShieldAlert } from "lucide-react";
import { getJson } from "./http";

type VolatilityEnvelope = {
  status: "READY" | "EMPTY";
  snapshot: {
    vix?: string;
    vixPercentile?: string;
    vixDelta1d?: string;
    vixDelta2d?: string;
    vixDelta5d?: string;
    vix3m?: string;
    vixTermRatio?: string;
    vixTermState: string;
    vxn?: string;
    vxnPercentile?: string;
    vxnDelta1d?: string;
    vxnDelta2d?: string;
    vxnDelta5d?: string;
    vxnVixRatio?: string;
    vxnVixSpread?: string;
    techStressState: string;
    quality: string;
  };
  dataAsOf: string;
};

type MacroBackgroundEnvelope = {
  status: "READY" | "EMPTY";
  snapshot: {
    tenYearYield?: string;
    twoYearYield?: string;
    fedFundsRate?: string;
    tenYearRealYield?: string;
    rateStress?: string;
    curveState: string;
    quality: string;
    includedInAggregateStress: false;
  };
  dataAsOf: string;
};

function number(value?: string, digits = 2) {
  return value === undefined ? "—" : Number(value).toFixed(digits);
}

function percentile(value?: string) {
  return value === undefined ? "—" : `${(Number(value) * 100).toFixed(0)}%`;
}

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

function stringArray(value?: string) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) &&
      parsed.every((item) => typeof item === "string")
      ? parsed
      : [];
  } catch {
    return [];
  }
}

export function MarketContextPage() {
  const regime = useQuery({
    queryKey: ["market-regime"],
    queryFn: () => requireData(api.GET("/api/v1/market/regime")),
  });
  const drawdown = useQuery({
    queryKey: ["portfolio-drawdown"],
    queryFn: () => requireData(api.GET("/api/v1/portfolio/drawdown")),
    retry: false,
  });
  const health = useQuery({
    queryKey: ["market-health"],
    queryFn: () => requireData(api.GET("/api/v1/market/data-health")),
  });
  const volatility = useQuery({
    queryKey: ["market-volatility"],
    queryFn: () => getJson<VolatilityEnvelope>("/api/v1/market/volatility"),
  });
  const macroBackground = useQuery({
    queryKey: ["macro-background"],
    queryFn: () =>
      getJson<MacroBackgroundEnvelope>("/api/v1/market/macro-background"),
  });
  const snapshot = regime.data?.snapshot;
  const drawdownSnapshot = drawdown.data?.snapshot;
  const volatilitySnapshot = volatility.data?.snapshot;
  const macroSnapshot = macroBackground.data?.snapshot;
  const stale =
    snapshot?.qualityStatus === "STALE" ||
    (health.data?.staleObservations ?? 0) > 0;

  return (
    <main className="inspection-shell">
      <header className="inspection-header context-header">
        <a href="/market-data" className="back-link">
          <ArrowLeft aria-hidden="true" /> Market data
        </a>
        <div>
          <p className="eyebrow">PACKET 03 / MARKET CONTEXT</p>
          <h1>Risk has a source.</h1>
        </div>
        <span
          className={`health-pill health-pill--${snapshot?.label?.toLowerCase() ?? "loading"}`}
        >
          <Gauge aria-hidden="true" />{" "}
          {snapshot?.label ?? regime.data?.status ?? "LOADING"}
        </span>
      </header>

      {stale ? (
        <aside className="quality-warning" role="alert">
          <AlertTriangle aria-hidden="true" />
          <span>
            <strong>Stale evidence</strong> STRONG_GREEN and precise advice are
            blocked until fresh observations arrive.
          </span>
        </aside>
      ) : null}
      {regime.isError ? (
        <aside className="error" role="alert">
          <strong>Regime unavailable</strong>
          <span>No classification was inferred from missing evidence.</span>
        </aside>
      ) : null}

      <section className="context-grid" aria-label="Volatility context">
        <article className="context-card">
          <p className="eyebrow">BROAD STRESS / VIX</p>
          <div className="drawdown-number">
            <strong>{number(volatilitySnapshot?.vix)}</strong>
            <span>5Y percentile {percentile(volatilitySnapshot?.vixPercentile)}</span>
          </div>
          <p>
            1D {number(volatilitySnapshot?.vixDelta1d)} · 2D{" "}
            {number(volatilitySnapshot?.vixDelta2d)} · 5D{" "}
            {number(volatilitySnapshot?.vixDelta5d)}
          </p>
        </article>

        <article className="context-card">
          <p className="eyebrow">VIX TERM STRUCTURE</p>
          <div className="drawdown-number">
            <strong>{volatilitySnapshot?.vixTermState ?? "MISSING"}</strong>
            <span>VIX / VIX3M {number(volatilitySnapshot?.vixTermRatio)}</span>
          </div>
          <p>VIX3M {number(volatilitySnapshot?.vix3m)}</p>
        </article>

        <article className="context-card">
          <p className="eyebrow">TECH OVERLAY / VXN</p>
          {volatilitySnapshot?.vxn === undefined ? (
            <p className="empty">暂无可靠 VXN 数据；广义市场判断仍可继续。</p>
          ) : (
            <>
              <div className="drawdown-number">
                <strong>{number(volatilitySnapshot.vxn)}</strong>
                <span>5Y percentile {percentile(volatilitySnapshot.vxnPercentile)}</span>
              </div>
              <p>
                1D {number(volatilitySnapshot.vxnDelta1d)} · 2D{" "}
                {number(volatilitySnapshot.vxnDelta2d)} · 5D{" "}
                {number(volatilitySnapshot.vxnDelta5d)}
              </p>
            </>
          )}
        </article>

        <article className="context-card">
          <p className="eyebrow">TECH PREMIUM</p>
          <div className="drawdown-number">
            <strong>{number(volatilitySnapshot?.vxnVixRatio)}</strong>
            <span>{volatilitySnapshot?.techStressState ?? "MISSING"}</span>
          </div>
          <p>VXN − VIX {number(volatilitySnapshot?.vxnVixSpread)}</p>
        </article>
      </section>

      <section aria-label="Macro background">
        <article className="context-card">
          <p className="eyebrow">宏观背景 / RATES &amp; CURVE</p>
          <dl className="score-breakdown">
            <div>
              <dt>10Y nominal yield</dt>
              <dd>{number(macroSnapshot?.tenYearYield)}%</dd>
            </div>
            <div>
              <dt>2Y nominal yield</dt>
              <dd>{number(macroSnapshot?.twoYearYield)}%</dd>
            </div>
            <div>
              <dt>Yield curve</dt>
              <dd>{macroSnapshot?.curveState ?? "MISSING"}</dd>
            </div>
            <div>
              <dt>Fed funds</dt>
              <dd>{number(macroSnapshot?.fedFundsRate)}%</dd>
            </div>
            <div>
              <dt>10Y real yield (DFII10)</dt>
              <dd>
                {macroSnapshot?.tenYearRealYield === undefined
                  ? "暂无可靠数据"
                  : `${number(macroSnapshot.tenYearRealYield)}%`}
              </dd>
            </div>
            <div>
              <dt>Rate context stress</dt>
              <dd>{number(macroSnapshot?.rateStress)}</dd>
            </div>
          </dl>
          <p className="quality-policy">
            利率与曲线仅作为宏观背景展示，尚未计入 aggregate market stress score。
          </p>
        </article>
      </section>

      <section className="context-grid">
        <article className="context-card context-card--regime">
          <p className="eyebrow">MARKET REGIME</p>
          {snapshot ? (
            <>
              <div className="regime-score">
                <strong>{snapshot.score?.toFixed(1)}</strong>
                <span>
                  / 100
                  <br />
                  {snapshot.confidence} confidence
                </span>
              </div>
              <dl className="score-breakdown">
                <div>
                  <dt>Trend</dt>
                  <dd>{snapshot.trendScore?.toFixed(1)} / 40</dd>
                </div>
                <div>
                  <dt>Momentum</dt>
                  <dd>{snapshot.momentumScore?.toFixed(1)} / 20</dd>
                </div>
                <div>
                  <dt>Breadth</dt>
                  <dd>{snapshot.breadthScore?.toFixed(1)} / 20</dd>
                </div>
                <div>
                  <dt>Stress</dt>
                  <dd>{snapshot.stressScore?.toFixed(1)} / 20</dd>
                </div>
              </dl>
              <ul className="narrative-list">
                {stringArray(snapshot.narrativesJson).map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </>
          ) : (
            <p className="empty">
              No versioned regime snapshot. WAIT_FOR_DATA.
            </p>
          )}
        </article>

        <article className="context-card">
          <p className="eyebrow">DRAWDOWN SOURCE</p>
          {drawdownSnapshot ? (
            <>
              <div className="drawdown-number">
                <strong>{drawdownSnapshot.drawdownPercent}%</strong>
                <span>{drawdownSnapshot.state}</span>
              </div>
              <p
                className={`source-tag source-tag--${drawdownSnapshot.marketDriven ? "market" : "specific"}`}
              >
                {drawdownSnapshot.sourceClassification}
              </p>
              <dl className="score-breakdown">
                <div>
                  <dt>SPY from peak</dt>
                  <dd>{drawdownSnapshot.spyDrawdownPercent}%</dd>
                </div>
                <div>
                  <dt>QQQ from peak</dt>
                  <dd>{drawdownSnapshot.qqqDrawdownPercent}%</dd>
                </div>
                <div>
                  <dt>High-water mark</dt>
                  <dd>{drawdownSnapshot.highWaterMark}</dd>
                </div>
                <div>
                  <dt>Confidence</dt>
                  <dd>{drawdownSnapshot.confidence}</dd>
                </div>
              </dl>
            </>
          ) : drawdown.isError ? (
            <p className="empty">
              Sign in is required to inspect private portfolio drawdown.
            </p>
          ) : (
            <p className="empty">No drawdown snapshot.</p>
          )}
        </article>

        <article className="context-card context-card--quality">
          <p className="eyebrow">
            <ShieldAlert aria-hidden="true" /> DATA QUALITY GATE
          </p>
          <dl className="quality-grid">
            <div>
              <dt>Stale</dt>
              <dd>{health.data?.staleObservations ?? "—"}</dd>
            </div>
            <div>
              <dt>Partial</dt>
              <dd>{health.data?.partialObservations ?? "—"}</dd>
            </div>
            <div>
              <dt>Suspect</dt>
              <dd>{health.data?.suspectObservations ?? "—"}</dd>
            </div>
            <div>
              <dt>Open events</dt>
              <dd>{health.data?.openQualityEvents ?? "—"}</dd>
            </div>
          </dl>
          <p className="quality-policy">
            Missing or stale evidence lowers confidence and blocks false
            precision. It never upgrades a classification.
          </p>
        </article>
      </section>

      <footer>
        <span>STRATEGY {snapshot?.strategyVersion ?? "—"}</span>
        <span>NO AUTO TRADING</span>
        <span>
          DATA AS OF {snapshot?.dataAsOf ?? health.data?.dataAsOf ?? "—"}
        </span>
      </footer>
    </main>
  );
}
