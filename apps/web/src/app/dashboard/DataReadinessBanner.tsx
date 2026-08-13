import type { components } from "@portfolio/api-client";
import { presentReadiness } from "../presentation/readiness-presentation";
import { DataCompletenessBadge } from "./DataCompletenessBadge";

type DataReadiness = components["schemas"]["DataReadiness"];
type AnalysisState = components["schemas"]["ExecutiveBrief"]["state"];

export function DataReadinessBanner({
  state,
  readiness,
}: {
  state: AnalysisState;
  headline: string;
  readiness: DataReadiness;
}) {
  const presentation = presentReadiness(
    state === "ANALYSIS_READY" ? readiness.status : state,
  );
  const affected =
    readiness.missingPositionCount + readiness.stalePositionCount;
  return (
    <section
      className="context-card data-readiness-banner"
      id="data-readiness"
      aria-live="polite"
    >
      <p className="eyebrow">分析状态</p>
      <h2>{presentation.label}</h2>
      <p>
        {affected > 0
          ? `${String(affected)} 个持仓仍在等待数据或需要更新。`
          : presentation.detail}
      </p>
      <DataCompletenessBadge value={readiness.completeness} />
      <details>
        <summary>查看缺少什么</summary>
        <p>
          市场数据覆盖 {(Number(readiness.marketCoverage) * 100).toFixed(0)}% ·
          基本面覆盖 {(Number(readiness.fundamentalCoverage) * 100).toFixed(0)}%
        </p>
        <small>
          缺失持仓 {String(readiness.missingPositionCount)} · 需要更新{" "}
          {String(readiness.stalePositionCount)} · 失败任务{" "}
          {String(readiness.failedJobCount)}
        </small>
      </details>
    </section>
  );
}
