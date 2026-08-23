import { api, type components } from "@portfolio/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { postJson } from "../http";
import { formatDateTime } from "../presentation/date-format";
import { formatPercent } from "../presentation/number-format";
import { presentAction } from "../presentation/action-presentation";
import { presentReadiness } from "../presentation/readiness-presentation";
import { WorkspaceNav } from "../workspace-nav";
import { ActionCard, type DashboardAction } from "./ActionCard";
import { DataReadinessBanner } from "./DataReadinessBanner";
import { EmptyPortfolioState } from "./EmptyPortfolioState";
import { PortfolioSafetySummary } from "./PortfolioSafetySummary";
import { TodayDecisionHero } from "./TodayDecisionHero";
import { TopRiskCard } from "./TopRiskCard";

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

function ActionSection({ actions }: { actions: DashboardAction[] }) {
  if (!actions.length) return null;
  return (
    <section className="brief-action-section" id="today-actions">
      <div className="section-title">
        <h2>今天需要处理的动作</h2>
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

export function ExecutiveDashboardPage() {
  const queryClient = useQueryClient();
  const brief = useQuery({
    queryKey: ["executive-brief-today"],
    queryFn: getExecutiveBrief,
    retry: false,
  });
  const reanalysis = useMutation({
    mutationFn: () =>
      postJson<{ runId: string; state: string }>("/api/v1/analysis/runs", {
        reason: "USER_REFRESH",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["executive-brief-today"],
      });
    },
  });
  return (
    <main className="shell workspace-shell dashboard-shell">
      <WorkspaceNav />
      <section className="workspace-heading brief-heading">
        <div>
          <p className="eyebrow">你的组合早报</p>
          <h1>今日简报</h1>
        </div>
        <div>
          <p>上次分析：{formatDateTime(brief.data?.dataAsOf)}</p>
          {brief.data && brief.data.state !== "NO_PORTFOLIO" ? (
            <button
              type="button"
              disabled={reanalysis.isPending}
              onClick={() => {
                reanalysis.mutate();
              }}
            >
              {reanalysis.isPending ? "正在启动…" : "重新分析"}
            </button>
          ) : null}
        </div>
      </section>
      {reanalysis.isError ? (
        <section className="context-card" role="alert">
          无法启动重新分析；现有结论不会被伪装成最新结果。
        </section>
      ) : null}
      {brief.isPending ? (
        <section className="context-card" aria-live="polite">
          <p className="empty-state">正在整理今天的组合结论…</p>
        </section>
      ) : brief.isError ? (
        <section className="context-card" role="alert">
          <h2>
            {brief.error instanceof BriefRequestError &&
            brief.error.status === 401
              ? "请先登录"
              : "今天的分析暂时无法读取"}
          </h2>
          <p>当前状态未知，系统不会把请求失败显示成“无需操作”。</p>
        </section>
      ) : brief.data.state === "NO_PORTFOLIO" ? (
        <EmptyPortfolioState />
      ) : (
        <>
          <TodayDecisionHero brief={brief.data} />
          {brief.data.recommendationNotice ? (
            <section className="context-card" aria-live="polite">
              {brief.data.recommendationNotice}
            </section>
          ) : null}
          <ActionSection actions={brief.data.todayPriorities} />
          <PortfolioSafetySummary brief={brief.data} />
          <DataReadinessBanner
            state={brief.data.state}
            headline={brief.data.headline}
            readiness={brief.data.dataReadiness}
          />
          <section className="brief-action-section">
            <div className="section-title">
              <h2>目前最需要留意的风险</h2>
              <span>{brief.data.topRisks.length}</span>
            </div>
            {brief.data.topRisks.length ? (
              <ol className="risk-list">
                {brief.data.topRisks.map((risk) => (
                  <TopRiskCard
                    key={`${risk.symbol ?? "portfolio"}-${risk.risk}`}
                    risk={risk}
                  />
                ))}
              </ol>
            ) : (
              <p className="context-card">
                当前没有已确认的前三项风险；数据不完整时系统不会把缺失解释为安全。
              </p>
            )}
          </section>
          <section className="brief-action-section holdings-brief">
            <div className="section-title">
              <h2>全部持仓一句话摘要</h2>
              <span>{brief.data.allHoldings.length}</span>
            </div>
            <div className="holdings-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>持仓</th>
                    <th>一句话建议</th>
                    <th>仓位</th>
                    <th>数据状态</th>
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
                      <td>{presentAction(holding.action).title}</td>
                      <td>{formatPercent(holding.currentWeight)}</td>
                      <td>{presentReadiness(holding.dataStatus).label}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <a className="market-link" href="/portfolio">
              查看完整持仓清单 →
            </a>
          </section>
          <details className="context-card audit-details">
            <summary>查看市场状态与分析依据</summary>
            <p>
              市场状态：{brief.data.market.summary}；从组合高点回撤：
              {formatPercent(brief.data.portfolio.drawdown)}；分析状态：
              {presentReadiness(brief.data.dataReadiness.status).label}。
            </p>
            <p>
              策略版本：{brief.data.strategyVersion ?? "未选择"}；数据更新：
              {formatDateTime(brief.data.dataAsOf)}
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
