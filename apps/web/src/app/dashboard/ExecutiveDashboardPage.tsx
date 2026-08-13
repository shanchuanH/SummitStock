import { api, type components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { ActionCard, type DashboardAction } from "./ActionCard";
import { DataReadinessBanner } from "./DataReadinessBanner";
import { EmptyPortfolioState } from "./EmptyPortfolioState";
import { PortfolioHealthCard } from "./PortfolioHealthCard";
import { WorkspaceNav } from "../workspace-nav";
import { presentAction } from "../presentation/action-presentation";
import { presentClassification } from "../presentation/classification-presentation";
import { presentPriority } from "../presentation/priority-presentation";
import { presentReadiness } from "../presentation/readiness-presentation";

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
function money(value?: string | number | null) {
  return value == null
    ? "—"
    : new Intl.NumberFormat("zh-CN", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 0,
      }).format(Number(value));
}
function percent(value?: string | null) {
  return value == null ? "—" : `${(Number(value) * 100).toFixed(1)}%`;
}
function asOf(value?: string | null) {
  return value
    ? `${new Intl.DateTimeFormat("zh-CN", { timeZone: "America/New_York", dateStyle: "medium", timeStyle: "short" }).format(new Date(value))} ET`
    : "待确认";
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
      <div className="section-title">
        <h2>{title}</h2>
        <span>{actions.length}</span>
      </div>
      <ol className="action-list">
        {actions.map((action) => (
          <ActionCard key={action.id} action={action} />
        ))}
      </ol>
    </section>
  );
}

function UnconfirmedActionState({ brief }: { brief: ExecutiveBrief }) {
  if (brief.confirmedNoAction) return null;
  const presentation =
    brief.state === "BLOCKED" || brief.state === "FAILED"
      ? {
          label: "BLOCKED",
          detail: "分析被安全门或失败任务阻塞，不能确认当前无需操作。",
        }
      : brief.state === "WAIT_FOR_MARKET_DATA" ||
          brief.state === "WAIT_FOR_FUNDAMENTALS"
        ? {
            label: "WAITING FOR DATA",
            detail: "所需数据尚未完整到达，不能确认当前无需操作。",
          }
        : brief.state === "PARTIAL_ANALYSIS" || brief.state === "STALE"
          ? {
              label: "ANALYSIS PARTIAL",
              detail: "分析不完整或已过期，不能确认当前无需操作。",
            }
          : null;
  return presentation ? (
    <section className="context-card action-state-warning" role="status">
      <strong>{presentation.label}</strong>
      <span>{presentation.detail}</span>
    </section>
  ) : null;
}

export function ExecutiveDashboardPage() {
  const brief = useQuery({
    queryKey: ["executive-brief-today"],
    queryFn: getExecutiveBrief,
    retry: false,
  });
  return (
    <main className="shell workspace-shell dashboard-shell">
      <WorkspaceNav />
      <section className="workspace-heading brief-heading">
        <div>
          <p className="eyebrow">EXECUTIVE BRIEF</p>
          <h1>今日简报</h1>
        </div>
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
              ? "请先登录"
              : "无法加载分析"}
          </h2>
          <p>当前状态未知，系统不会把请求失败显示成无需操作。</p>
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
          <section className="terminal-overview">
            <article>
              <span>市场状态</span>
              <strong>{brief.data.market.regime}</strong>
              <small>{brief.data.market.summary}</small>
            </article>
            <article>
              <span>组合回撤</span>
              <strong>{percent(brief.data.portfolio.drawdown)}</strong>
              <small>
                {brief.data.portfolio.drawdownSource ?? "来源待确认"}
              </small>
            </article>
            <article>
              <span>可投资资产</span>
              <strong>{money(brief.data.capital.investableAssets)}</strong>
              <small>
                可部署现金 {money(brief.data.capital.deployableCash)}
              </small>
            </article>
            <article>
              <span>数据截至</span>
              <strong className="as-of-value">
                {asOf(brief.data.dataAsOf)}
              </strong>
              <small>{brief.data.dataReadiness.status}</small>
            </article>
          </section>
          <section className="brief-counts" aria-label="今日项目统计">
            <article>
              <strong>{brief.data.mustAct.length}</strong>
              <span>需要行动</span>
            </article>
            <article>
              <strong>{brief.data.doNot.length}</strong>
              <span>不要执行</span>
            </article>
            <article>
              <strong>{brief.data.watch.length}</strong>
              <span>继续观察</span>
            </article>
            <article>
              <strong>{brief.data.blocked.length}</strong>
              <span>数据受阻</span>
            </article>
          </section>
          {brief.data.confirmedNoAction ? (
            <section className="context-card calm-state">
              <strong>当前无需紧急操作</strong>
              <span>分析已完成、覆盖健康，且没有生效的行动建议。</span>
            </section>
          ) : (
            <UnconfirmedActionState brief={brief.data} />
          )}
          <ActionSection
            title="今日优先动作"
            actions={brief.data.todayPriorities}
          />
          <section className="context-card">
            <p className="eyebrow">PORTFOLIO HEALTH</p>
            <h2>组合健康</h2>
            <dl className="health-metrics owner-health-metrics">
              <div>
                <dt>可投资资产</dt>
                <dd>{money(brief.data.capital.investableAssets)}</dd>
              </div>
              <div>
                <dt>Emergency Cash</dt>
                <dd>{money(brief.data.capital.emergencyReserve)}</dd>
              </div>
              <div>
                <dt>Strategy Drawdown</dt>
                <dd>{percent(brief.data.portfolio.drawdown)}</dd>
              </div>
              <div>
                <dt>Tactical + Speculative</dt>
                <dd>
                  {percent(
                    brief.data.summary.tacticalSpeculativeExposureFraction,
                  )}
                </dd>
              </div>
              <div>
                <dt>数据完整度</dt>
                <dd>{percent(brief.data.dataReadiness.completeness)}</dd>
              </div>
            </dl>
          </section>
          <section className="brief-action-section">
            <div className="section-title">
              <h2>最大风险</h2>
              <span>{brief.data.topRisks.length}</span>
            </div>
            {brief.data.topRisks.length ? (
              <ol className="risk-list">
                {brief.data.topRisks.map((risk) => (
                  <li
                    className="context-card"
                    key={`${risk.symbol ?? "portfolio"}-${risk.risk}`}
                  >
                    <strong>{risk.risk}</strong>
                    <p>
                      <b>这意味着什么：</b>
                      {risk.meaning}
                    </p>
                    <p>
                      <b>现在应该做什么：</b>
                      {risk.nowAction}
                    </p>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="context-card">
                当前没有已确认的前三项风险；数据不完整时系统不会把缺失解释为安全。
              </p>
            )}
          </section>
          <section className="brief-action-section">
            <div className="section-title">
              <h2>全部持仓摘要</h2>
              <span>{brief.data.allHoldings.length}</span>
            </div>
            <div className="holdings-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>持仓</th>
                    <th>分类</th>
                    <th>优先级</th>
                    <th>建议</th>
                    <th>当前仓位</th>
                    <th>数据</th>
                  </tr>
                </thead>
                <tbody>
                  {brief.data.allHoldings.map((holding) => (
                    <tr key={holding.positionId}>
                      <td>
                        <a href={`/positions/${holding.positionId}`}>
                          {holding.symbol}
                        </a>
                        <small>{holding.companyName}</small>
                      </td>
                      <td>{presentClassification(holding.classification)}</td>
                      <td>
                        <span
                          className={`priority-pill ${presentPriority(holding.priority).tone}`}
                        >
                          {presentPriority(holding.priority).label}
                        </span>
                      </td>
                      <td>{presentAction(holding.action).shortTitle}</td>
                      <td>{percent(holding.currentWeight)}</td>
                      <td>{presentReadiness(holding.dataStatus).label}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="dashboard-grid">
            <PortfolioHealthCard health={brief.data.portfolioHealth} />
            <article className="context-card">
              <p className="eyebrow">PORTFOLIO</p>
              <h2>{brief.data.summary.openPositions} 个持仓</h2>
              <p>
                全部持仓已按“今天处理、现在不要做、继续观察、暂无动作”排序。
              </p>
            </article>
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
