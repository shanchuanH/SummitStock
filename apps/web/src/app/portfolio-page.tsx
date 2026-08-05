import { api, type components } from "@portfolio/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, ArrowLeft, CheckCircle2, ShieldX } from "lucide-react";
import { useState } from "react";

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

type Suggestion = components["schemas"]["ClassificationSuggestionResponse"];

export function PortfolioPage() {
  const queryClient = useQueryClient();
  const [symbol, setSymbol] = useState("DXYZ");
  const [suggestion, setSuggestion] = useState<Suggestion>();
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

  const classify = useMutation({
    mutationFn: async () => {
      const result = await requireData(
        api.GET("/api/v1/positions/classification-suggestion", {
          params: {
            query: {
              symbol,
              assetType: "EQUITY",
              thematic: false,
              unvestedCompensation: false,
            },
          },
        }),
      );
      setSuggestion(result);
      return result;
    },
  });

  const confirm = useMutation({
    mutationFn: async () => {
      const position = positions.data?.[0];
      if (!position?.id || !suggestion?.classification)
        throw new Error(
          "A position and classification suggestion are required",
        );
      const csrf = await requireData(api.GET("/api/v1/auth/csrf", {}));
      await requireData(
        api.POST("/api/v1/positions/{id}/classify", {
          params: { path: { id: position.id } },
          headers: { [csrf.headerName ?? "X-CSRF-TOKEN"]: csrf.token ?? "" },
          body: {
            classification: suggestion.classification,
            expectedVersion: position.version ?? 0,
          },
        }),
      );
      await queryClient.invalidateQueries({ queryKey: ["positions"] });
    },
  });

  const preview = useMutation({
    mutationFn: async () => {
      const csrf = await requireData(api.GET("/api/v1/auth/csrf", {}));
      return requireData(
        api.POST("/api/v1/trade-plans/preview", {
          headers: { [csrf.headerName ?? "X-CSRF-TOKEN"]: csrf.token ?? "" },
          body: {
            classification: "QUALITY_STOCK",
            classificationConfirmed: true,
            currentWeight: 0.08,
            projectedWeight: 0.09,
            proposedTradeRisk: 0.002,
            currentOpenStockRisk: 0.005,
            currentClusterRisk: 0.002,
            averagingDown: false,
            thesisImproving: false,
            anchoredToCostBasis: false,
            quality: "STALE",
          },
        }),
      );
    },
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
            <p className="empty">NO URGENT ACTION</p>
          ) : null}
        </article>
        <article className="context-card">
          <p className="eyebrow">CLASSIFICATION ASSISTANT</p>
          <form
            className="compact-form"
            onSubmit={(event) => {
              event.preventDefault();
              classify.mutate();
            }}
          >
            <label>
              Symbol
              <input
                value={symbol}
                onChange={(event) => {
                  setSymbol(event.target.value);
                }}
              />
            </label>
            <button type="submit">Suggest classification</button>
          </form>
          {suggestion ? (
            <div className="policy-result">
              <strong>{suggestion.classification}</strong>
              <span>{suggestion.reason}</span>
              <small>User confirmation is always required.</small>
              {suggestion.classification !== "UNKNOWN" ? (
                <button
                  type="button"
                  onClick={() => {
                    confirm.mutate();
                  }}
                >
                  Confirm for first position
                </button>
              ) : null}
            </div>
          ) : null}
        </article>
        <article className="context-card">
          <p className="eyebrow">TRADE PLAN BUILDER</p>
          <p className="quality-policy">
            Preview a Quality Stock plan against hard weight, trade, total,
            cluster, cooling, and evidence gates.
          </p>
          <button
            type="button"
            onClick={() => {
              preview.mutate();
            }}
          >
            Preview stale-data plan
          </button>
          {preview.data ? (
            <div className="policy-result" role="status">
              {preview.data.allowed ? (
                <CheckCircle2 aria-hidden="true" />
              ) : (
                <ShieldX aria-hidden="true" />
              )}
              <strong>
                {preview.data.allowed ? "RISK LIMITS PASS" : "BLOCKED"}
              </strong>
              <span>Confidence: {preview.data.confidence}</span>
              {!preview.data.preciseQuantityAllowed ? (
                <span className="precision-block">
                  <AlertTriangle aria-hidden="true" /> Exact quantity
                  unavailable until evidence is healthy.
                </span>
              ) : null}
              <small>{preview.data.ruleIds?.join(" · ")}</small>
            </div>
          ) : null}
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
