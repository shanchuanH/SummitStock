import type { components } from "@portfolio/api-client";

type DataReadiness = components["schemas"]["DataReadiness"];
type AnalysisState = NonNullable<
  components["schemas"]["ExecutiveBrief"]["state"]
>;

export function DataReadinessBanner({
  state,
  headline,
  readiness,
}: {
  state: AnalysisState;
  headline: string;
  readiness: DataReadiness;
}) {
  return (
    <section className="context-card" aria-live="polite">
      <p className="eyebrow">ANALYSIS READINESS · {state}</p>
      <h2>{headline}</h2>
      <p>
        Data: {readiness.status} · market coverage fraction{" "}
        {readiness.marketCoverage} · fundamental coverage fraction{" "}
        {readiness.fundamentalCoverage}
      </p>
      <small>
        Missing positions {String(readiness.missingPositionCount)} · stale
        positions {String(readiness.stalePositionCount)} · failed jobs{" "}
        {String(readiness.failedJobCount)}
      </small>
    </section>
  );
}
