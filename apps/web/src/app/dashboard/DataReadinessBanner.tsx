import type { components } from "@portfolio/api-client";
type DataReadiness=components["schemas"]["DataReadiness"];
type AnalysisState=components["schemas"]["ExecutiveBrief"]["state"];
export function DataReadinessBanner({state,headline,readiness}:{state:AnalysisState;headline:string;readiness:DataReadiness}){
  return <section className="context-card data-readiness-banner" aria-live="polite"><p className="eyebrow">ANALYSIS READINESS · {state}</p><h2>{headline}</h2><p>数据状态：{readiness.status} · 市场数据覆盖率 {readiness.marketCoverage} · 基本面覆盖率 {readiness.fundamentalCoverage}</p><small>缺失持仓 {String(readiness.missingPositionCount)} · 过期持仓 {String(readiness.stalePositionCount)} · 失败任务 {String(readiness.failedJobCount)}</small></section>;
}
