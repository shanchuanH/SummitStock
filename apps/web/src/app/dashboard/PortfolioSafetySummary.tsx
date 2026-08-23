import type { components } from "@portfolio/api-client";
import {
  Banknote,
  CircleDollarSign,
  ShieldCheck,
  TrendingDown,
} from "lucide-react";
import { formatMoney, formatPercent } from "../presentation/number-format";

type ExecutiveBrief = components["schemas"]["ExecutiveBrief"];

export function PortfolioSafetySummary({ brief }: { brief: ExecutiveBrief }) {
  return (
    <section
      className="portfolio-safety"
      aria-labelledby="portfolio-safety-title"
    >
      <div className="section-title">
        <h2 id="portfolio-safety-title">组合安全状态</h2>
      </div>
      <div className="portfolio-safety-grid">
        <article className="context-card">
          <ShieldCheck aria-hidden="true" />
          <span>生活备用金</span>
          <strong>{formatMoney(brief.capital.emergencyReserve)}</strong>
          <small>已从可部署资金中保护</small>
        </article>
        <article className="context-card">
          <TrendingDown aria-hidden="true" />
          <span>从组合高点回撤</span>
          {brief.portfolio.drawdownSource === "NAV_RECONCILIATION_REQUIRED" ? (
            <>
              <strong>组合回撤暂未确认</strong>
              <small>检测到现金变化，正在区分投资收益与外部入金。</small>
            </>
          ) : (
            <>
              <strong>{formatPercent(brief.portfolio.drawdown)}</strong>
              <small>{brief.portfolio.drawdownSource ?? "回撤来源正在确认"}</small>
            </>
          )}
        </article>
        <article className="context-card">
          <Banknote aria-hidden="true" />
          <span>主动与投机仓位</span>
          <strong>
            {formatPercent(brief.summary.tacticalSpeculativeExposureFraction)}
          </strong>
          <small>
            {brief.portfolioHealth.reasons[0] ?? "当前没有已确认的集中度警告"}
          </small>
        </article>
        <article className="context-card">
          <CircleDollarSign aria-hidden="true" />
          <span>可部署现金</span>
          <strong>{formatMoney(brief.capital.deployableCash)}</strong>
          <small>不包含生活备用金</small>
        </article>
      </div>
    </section>
  );
}
