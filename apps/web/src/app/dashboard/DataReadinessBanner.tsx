import type { components } from "@portfolio/api-client";
import { DataCompletenessBadge } from "./DataCompletenessBadge";
type DataReadiness = components["schemas"]["DataReadiness"];
type AnalysisState = components["schemas"]["ExecutiveBrief"]["state"];
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
    <section className="context-card data-readiness-banner" aria-live="polite">
      <p className="eyebrow">分析准备状态 · {state}</p>
      <h2>{headline}</h2>
      <DataCompletenessBadge value={readiness.completeness} />
      <p>
        市场数据覆盖 {(Number(readiness.marketCoverage) * 100).toFixed(0)}% ·
        基本面覆盖 {(Number(readiness.fundamentalCoverage) * 100).toFixed(0)}%
      </p>
      <small>
        缺失持仓 {String(readiness.missingPositionCount)} · 过期持仓{" "}
        {String(readiness.stalePositionCount)} · 失败任务{" "}
        {String(readiness.failedJobCount)}
      </small>
    </section>
  );
}
