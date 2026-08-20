import type { components } from "@portfolio/api-client";
import { AlertTriangle, CheckCircle2, Clock3, ShieldAlert } from "lucide-react";
import { presentAction } from "../presentation/action-presentation";

type ExecutiveBrief = components["schemas"]["ExecutiveBrief"];

const waitingStates = new Set<ExecutiveBrief["state"]>([
  "IMPORT_PENDING_CONFIRMATION",
  "IMPORTING",
  "PORTFOLIO_READY",
  "ANALYSIS_QUEUED",
  "WAIT_FOR_MARKET_DATA",
  "WAIT_FOR_FUNDAMENTALS",
  "PARTIAL_ANALYSIS",
  "STALE",
]);

export function TodayDecisionHero({ brief }: { brief: ExecutiveBrief }) {
  const urgent = brief.todayPriorities.filter(
    (action) => action.priority === "MUST_ACT",
  );
  const incomplete =
    brief.dataReadiness.missingPositionCount +
    brief.dataReadiness.stalePositionCount;

  if (brief.state === "BLOCKED" || brief.state === "FAILED") {
    return (
      <section className="today-decision-hero danger" aria-live="polite">
        <ShieldAlert aria-hidden="true" />
        <div>
          <p className="eyebrow">今日结论</p>
          <h2>今天的分析没有完整跑完</h2>
          <p>已有结论可能不完整。在问题修复前，不提供新的精确交易数量。</p>
          <a href="#data-readiness">查看遇到了什么问题</a>
        </div>
      </section>
    );
  }

  if (
    waitingStates.has(brief.state) ||
    (!brief.confirmedNoAction && incomplete > 0)
  ) {
    return (
      <section className="today-decision-hero warning" aria-live="polite">
        <Clock3 aria-hidden="true" />
        <div>
          <p className="eyebrow">今日结论</p>
          <h2>今天先不要根据 SummitStock 下新决定</h2>
          <p>
            {incomplete > 0
              ? `${String(incomplete)} 个持仓的数据尚未完整。`
              : "组合分析仍在进行。"}
            系统不会把“不知道”显示成“无需操作”。
          </p>
          <a href="#data-readiness">查看缺少什么</a>
        </div>
      </section>
    );
  }

  if (urgent.length > 0) {
    const unaffected = Math.max(
      0,
      brief.allHoldings.length - brief.mustAct.length,
    );
    return (
      <section className="today-decision-hero danger" aria-live="polite">
        <AlertTriangle aria-hidden="true" />
        <div>
          <p className="eyebrow">今日结论</p>
          <h2>今天有 {urgent.length} 件事需要你处理</h2>
          <ol>
            {urgent.map((action) => (
              <li key={action.id}>
                <strong>{action.symbol ?? "组合"}</strong> —{" "}
                {presentAction(action.action).shortTitle}
              </li>
            ))}
          </ol>
          {unaffected > 0 ? (
            <p>其余 {unaffected} 个持仓暂无紧急动作。</p>
          ) : null}
          <a href="#today-actions">查看今天的动作</a>
        </div>
      </section>
    );
  }

  if (brief.confirmedNoAction && brief.state === "ANALYSIS_READY") {
    return (
      <section className="today-decision-hero positive" aria-live="polite">
        <CheckCircle2 aria-hidden="true" />
        <div>
          <p className="eyebrow">今日结论</p>
          <h2>今天不需要做交易</h2>
          <p>组合分析已完成，当前没有触发减仓、加仓或风险动作。</p>
        </div>
      </section>
    );
  }

  return (
    <section className="today-decision-hero warning" aria-live="polite">
      <Clock3 aria-hidden="true" />
      <div>
        <p className="eyebrow">今日结论</p>
        <h2>今天先等待分析确认</h2>
        <p>当前状态还不足以确认“无需操作”，系统不会生成假精确结论。</p>
      </div>
    </section>
  );
}
