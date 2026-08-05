import { api } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { Activity, Database, LockKeyhole, ShieldCheck } from "lucide-react";

async function fetchVersion() {
  const { data, error, response } = await api.GET("/api/v1/version");
  if (data === undefined) {
    throw new Error(
      `API request failed (${String(response.status)}): ${JSON.stringify(error)}`,
    );
  }
  return data;
}

export function HealthPage() {
  const version = useQuery({
    queryKey: ["api-version"],
    queryFn: fetchVersion,
    refetchInterval: 60_000,
  });
  const state = version.isPending
    ? "CONNECTING"
    : version.isError
      ? "UNAVAILABLE"
      : "READY";

  return (
    <main className="shell">
      <header className="masthead">
        <a className="brand" href="/" aria-label="Portfolio Engine home">
          <span className="brand-mark" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
          <span>
            PORTFOLIO
            <br />
            ENGINE
          </span>
        </a>
        <span className={`status status--${state.toLowerCase()}`}>
          <i />
          {state}
        </span>
      </header>

      <section className="hero">
        <p className="eyebrow">FOUNDATION / MANUAL EXECUTION ONLY</p>
        <h1>
          Risk discipline,
          <br />
          <em>before</em> conviction.
        </h1>
        <p className="lede">
          A private decision-support system for explainable portfolio risk,
          deliberate entries, and auditable restraint.
        </p>
      </section>

      <section className="system-grid" aria-label="System status">
        <article>
          <Activity aria-hidden="true" />
          <span>API runtime</span>
          <strong>{version.data?.runtimeMode ?? "—"}</strong>
          <small>Spring Boot 4.1</small>
        </article>
        <article>
          <Database aria-hidden="true" />
          <span>Source of truth</span>
          <strong>MYSQL 8.4</strong>
          <small>UTC · utf8mb4 · InnoDB</small>
        </article>
        <article>
          <LockKeyhole aria-hidden="true" />
          <span>Execution</span>
          <strong>MANUAL</strong>
          <small>Fidelity read-only boundary</small>
        </article>
        <article>
          <ShieldCheck aria-hidden="true" />
          <span>Strategy</span>
          <strong>{version.data?.strategyVersion ?? "1.0.0-draft"}</strong>
          <small>Draft · not published</small>
        </article>
      </section>

      <a className="market-link" href="/market-data">
        Inspect market data ledger →
      </a>

      {version.isError ? (
        <aside className="error" role="alert">
          <strong>Backend unavailable</strong>
          <span>
            Start MySQL and the API runtime. No investment data or
            recommendation has been fabricated.
          </span>
        </aside>
      ) : null}

      <footer>
        <span>NO AUTO TRADING</span>
        <span>NO URGENT ACTION is the normal state</span>
        <span>Packets 01–03 scaffold</span>
      </footer>
    </main>
  );
}
