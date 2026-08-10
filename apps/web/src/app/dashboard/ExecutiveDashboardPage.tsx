import { api, type components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { ActionCard, type DashboardAction } from "./ActionCard";
import { DataReadinessBanner } from "./DataReadinessBanner";
import { EmptyPortfolioState } from "./EmptyPortfolioState";
import { PortfolioHealthCard } from "./PortfolioHealthCard";
import { WorkspaceNav } from "../workspace-nav";

type ExecutiveBrief = components["schemas"]["ExecutiveBrief"];
class BriefRequestError extends Error { constructor(readonly status: number) { super(`Executive brief unavailable (${String(status)})`); } }
async function getExecutiveBrief(): Promise<ExecutiveBrief> {
  const { data, response } = await api.GET("/api/v1/brief/today");
  if (!data) throw new BriefRequestError(response.status);
  return data;
}
function money(value?: string | number | null) { return value == null ? "—" : new Intl.NumberFormat("zh-CN", { style: "currency", currency: "USD", maximumFractionDigits: 0 }).format(Number(value)); }
function percent(value?: string | null) { return value == null ? "—" : `${(Number(value) * 100).toFixed(1)}%`; }
function asOf(value?: string | null) { return value ? `${new Intl.DateTimeFormat("zh-CN", { timeZone: "America/New_York", dateStyle: "medium", timeStyle: "short" }).format(new Date(value))} ET` : "待确认"; }

function ActionSection({ title, actions }: { title: string; actions: DashboardAction[] }) {
  if (!actions.length) return null;
  return <section className="brief-action-section"><div className="section-title"><h2>{title}</h2><span>{actions.length}</span></div><ol className="action-list">{actions.map(action => <ActionCard key={action.id} action={action} />)}</ol></section>;
}

export function ExecutiveDashboardPage() {
  const brief = useQuery({ queryKey: ["executive-brief-today"], queryFn: getExecutiveBrief, retry: false });
  return (
    <main className="shell workspace-shell dashboard-shell">
      <WorkspaceNav />
      <section className="workspace-heading brief-heading"><div><p className="eyebrow">EXECUTIVE BRIEF</p><h1>今日简报</h1></div><p>数据截至 {asOf(brief.data?.dataAsOf)}</p></section>
      {brief.isPending ? <section className="context-card"><p className="empty-state">正在加载分析…</p></section>
      : brief.isError ? <section className="context-card" role="alert"><h2>{brief.error instanceof BriefRequestError && brief.error.status === 401 ? "请先登录" : "无法加载分析"}</h2><p>当前状态未知，系统不会把请求失败显示成无需操作。</p></section>
      : brief.data.state === "NO_PORTFOLIO" ? <EmptyPortfolioState />
      : <>
        <DataReadinessBanner state={brief.data.state} headline={brief.data.headline} readiness={brief.data.dataReadiness} />
        <section className="terminal-overview">
          <article><span>市场状态</span><strong>{brief.data.portfolioHealth.status}</strong><small>{brief.data.headline}</small></article>
          <article><span>组合回撤</span><strong>{percent(brief.data.summary.portfolioDrawdownFraction)}</strong><small>{brief.data.summary.drawdownSource ?? "来源待确认"}</small></article>
          <article><span>可投资现金</span><strong>{money(Number(brief.data.summary.trackedCash) - Number(brief.data.summary.emergencyCash))}</strong><small>已扣除应急现金</small></article>
          <article><span>数据截至</span><strong className="as-of-value">{asOf(brief.data.dataAsOf)}</strong><small>{brief.data.dataReadiness.status}</small></article>
        </section>
        <section className="brief-counts" aria-label="今日项目统计">
          <article><strong>{brief.data.mustAct.length}</strong><span>需要行动</span></article>
          <article><strong>{brief.data.doNot.length}</strong><span>不要执行</span></article>
          <article><strong>{brief.data.watch.length}</strong><span>继续观察</span></article>
          <article><strong>{brief.data.dataReadiness.missingPositionCount + brief.data.dataReadiness.stalePositionCount}</strong><span>数据受阻</span></article>
        </section>
        {!brief.data.mustAct.length && !brief.data.doNot.length && !brief.data.watch.length ? <section className="context-card calm-state"><strong>当前无需紧急操作</strong><span>分析已完成，没有生效的行动建议。</span></section> : null}
        <ActionSection title="必须行动" actions={brief.data.mustAct.slice(0, 3)} />
        <ActionSection title="不要执行" actions={brief.data.doNot} />
        <ActionSection title="持续观察" actions={brief.data.watch} />
        <section className="dashboard-grid"><article className="context-card"><p className="eyebrow">组合概览</p><h2>{brief.data.summary.openPositions} 个持仓</h2><dl className="health-metrics"><div><dt>流动资产</dt><dd>{money(brief.data.summary.totalLiquidAssets)}</dd></div><div><dt>已投资 / 现金</dt><dd>{money(brief.data.summary.investedValue)} / {money(brief.data.summary.trackedCash)}</dd></div><div><dt>核心 / 战术</dt><dd>{percent(brief.data.summary.coreExposureFraction)} / {percent(brief.data.summary.tacticalExposureFraction)}</dd></div><div><dt>集群风险</dt><dd>{percent(brief.data.summary.clusterRiskFraction)}</dd></div></dl></article><PortfolioHealthCard health={brief.data.portfolioHealth} /></section>
        <details className="context-card audit-details"><summary>数据与审计依据</summary><p>策略版本：{brief.data.strategyVersion ?? "未选择"}；数据截至：{asOf(brief.data.dataAsOf)}</p></details>
      </>}
      <footer><span>仅供决策支持</span><span>确认不等于执行</span><span>不会自动交易</span></footer>
    </main>
  );
}
