import { api, type components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { ActionCard, type DashboardAction } from "./ActionCard";
import { DataReadinessBanner } from "./DataReadinessBanner";
import { EmptyPortfolioState } from "./EmptyPortfolioState";
import { PortfolioHealthCard } from "./PortfolioHealthCard";
import { WorkspaceNav } from "../workspace-nav";

type ExecutiveBrief = components["schemas"]["ExecutiveBrief"];

class BriefRequestError extends Error {
  constructor(readonly status: number) {
    super(`Executive brief unavailable (${String(status)})`);
  }
}

async function getExecutiveBrief(): Promise<ExecutiveBrief> {
  const { data, response } = await api.GET("/api/v1/brief/today");
  if (!data) throw new BriefRequestError(response.status);
  return data;
}

function money(value: string) {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "USD",
  }).format(Number(value));
}

function asOf(value?: string | null) {
  if (!value) return "数据时间待确认";
  return `${new Intl.DateTimeFormat("zh-CN", {
    timeZone: "America/New_York",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value))} ET`;
}

function SummaryCard({ brief }: { brief: ExecutiveBrief }) {
  return (
    <article className="context-card">
      <p className="eyebrow">组合概览</p>
      <h2>{brief.summary.openPositions} 个持仓</h2>
      <dl className="health-metrics">
        <div>
          <dt>已投资资产</dt>
          <dd>{money(brief.summary.investedValue)}</dd>
        </div>
        <div>
          <dt>已跟踪现金</dt>
          <dd>{money(brief.summary.trackedCash)}</dd>
        </div>
        <div>
          <dt>应急现金</dt>
          <dd>{money(brief.summary.emergencyCash)}</dd>
        </div>
        <div>
          <dt>战术储备</dt>
          <dd>{money(brief.summary.tacticalReserve)}</dd>
        </div>
      </dl>
    </article>
  );
}

function ActionSection({
  title,
  actions,
}: {
  title: string;
  actions: DashboardAction[];
}) {
  if (!actions.length) return null;
  return (
    <section className="brief-action-section">
      <h2>{title}</h2>
      <ol className="action-list">
        {actions.map((action) => (
          <ActionCard key={action.id} action={action} />
        ))}
      </ol>
    </section>
  );
}

export function ExecutiveDashboardPage() {
  const brief = useQuery({
    queryKey: ["executive-brief-today"],
    queryFn: getExecutiveBrief,
    retry: false,
  });
  return (
    <main className="shell workspace-shell">
      <WorkspaceNav />
      <section className="workspace-heading brief-heading">
        <h1>今日简报</h1>
        <p>数据截至 {asOf(brief.data?.dataAsOf)}</p>
      </section>
      {brief.isPending ? (
        <section className="context-card">
          <p className="empty-state">正在加载分析…</p>
        </section>
      ) : brief.isError ? (
        <section className="context-card" role="alert">
          <h2>
            {brief.error instanceof BriefRequestError &&
            brief.error.status === 401
              ? "请登录"
              : "无法加载分析"}
          </h2>
          <p>
            {brief.error instanceof BriefRequestError &&
            brief.error.status === 401
              ? "请前往设置登录后查看私人持仓建议。"
              : "当前分析状态未知，系统不会把请求失败显示成无需操作。"}
          </p>
        </section>
      ) : brief.data.state === "NO_PORTFOLIO" ? (
        <EmptyPortfolioState />
      ) : (
        <>
          <DataReadinessBanner
            state={brief.data.state}
            headline={brief.data.headline}
            readiness={brief.data.dataReadiness}
          />
          <section className="brief-counts" aria-label="今日项目统计">
            <article>
              <strong>{brief.data.mustAct.length}</strong>
              <span>项需要处理</span>
            </article>
            <article>
              <strong>
                {brief.data.doNot.length + brief.data.watch.length}
              </strong>
              <span>项需要观察</span>
            </article>
            <article>
              <strong>
                {Math.max(
                  0,
                  brief.data.summary.openPositions -
                    brief.data.mustAct.length -
                    brief.data.doNot.length -
                    brief.data.watch.length,
                )}
              </strong>
              <span>项无需动作</span>
            </article>
          </section>
          {brief.data.state === "ANALYSIS_READY" &&
          !brief.data.mustAct.length &&
          !brief.data.doNot.length &&
          !brief.data.watch.length ? (
            <section className="context-card calm-state">
              <strong>当前无需紧急操作</strong>
              <span>分析已完成，当前没有生效的行动建议。</span>
            </section>
          ) : null}
          <ActionSection
            title="需要处理"
            actions={brief.data.mustAct.slice(0, 3)}
          />
          <ActionSection
            title="不要执行"
            actions={brief.data.doNot}
          />
          <ActionSection
            title="持续观察"
            actions={brief.data.watch}
          />
          <section className="dashboard-grid">
            <SummaryCard brief={brief.data} />
            <PortfolioHealthCard health={brief.data.portfolioHealth} />
          </section>
          <details className="context-card audit-details">
            <summary>数据与审计依据</summary>
            <p>
              策略版本：{brief.data.strategyVersion ?? "未选择"}；数据截至：
              {asOf(brief.data.dataAsOf)}
            </p>
          </details>
        </>
      )}
      <footer>
        <span>仅供决策支持</span>
        <span>确认不等于执行</span>
        <span>不会自动交易</span>
      </footer>
    </main>
  );
}
