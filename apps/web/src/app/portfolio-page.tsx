import type { components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ClassificationModal } from "./classification-modal";
import { getJson } from "./http";
import { presentAction } from "./presentation/action-presentation";
import { presentClassification } from "./presentation/classification-presentation";
import { presentConfidence } from "./presentation/confidence-presentation";
import { formatDate } from "./presentation/date-format";
import { formatMoney, formatPercent } from "./presentation/number-format";
import { presentPriority } from "./presentation/priority-presentation";
import { presentReadiness } from "./presentation/readiness-presentation";
import { WorkspaceNav } from "./workspace-nav";

export type PortfolioHolding =
  components["schemas"]["PortfolioHoldingResponse"];
const filters = [
  "全部",
  "需要处理",
  "超权重",
  "数据缺失",
  "即将财报",
  "Core",
  "Tactical",
  "Speculative",
] as const;
type Filter = (typeof filters)[number];
const priorityRank: Record<string, number> = {
  MUST_ACT: 0,
  DO_NOT: 1,
  WATCH: 2,
  NORMAL: 3,
};

function target(row: PortfolioHolding) {
  return row.targetWeightMin == null && row.targetWeightMax == null
    ? "暂无可靠数据"
    : `${formatPercent(row.targetWeightMin)}–${formatPercent(row.targetWeightMax)}`;
}

function matches(row: PortfolioHolding, filter: Filter) {
  if (filter === "全部") return true;
  if (filter === "需要处理") return row.priority === "MUST_ACT";
  if (filter === "超权重")
    return (
      row.targetWeightMax != null &&
      Number(row.currentWeight) > Number(row.targetWeightMax)
    );
  if (filter === "数据缺失")
    return !["READY", "HEALTHY"].includes(row.dataStatus ?? "");
  if (filter === "即将财报")
    return Boolean(
      row.nextEvent &&
      new Date(row.nextEvent).getTime() < Date.now() + 30 * 86_400_000,
    );
  if (filter === "Core")
    return row.bucket === "CORE" || row.classification?.startsWith("CORE_");
  if (filter === "Tactical")
    return (
      row.bucket === "TACTICAL" ||
      row.classification?.includes("TACTICAL") ||
      row.classification === "THEMATIC_ETF"
    );
  return row.classification === "SPECULATIVE";
}

async function loadHoldings() {
  return getJson<PortfolioHolding[]>("/api/v1/portfolio/holdings");
}

export function PortfolioPage() {
  const holdings = useQuery({
    queryKey: ["portfolio-holdings"],
    queryFn: loadHoldings,
    retry: false,
  });
  const [filter, setFilter] = useState<Filter>("全部");
  const [classificationTarget, setClassificationTarget] =
    useState<PortfolioHolding>();
  const rows = useMemo(
    () =>
      [...(holdings.data ?? [])]
        .filter((row) => matches(row, filter))
        .sort(
          (a, b) =>
            (priorityRank[a.priority ?? ""] ?? 4) -
              (priorityRank[b.priority ?? ""] ?? 4) ||
            (a.symbol ?? "").localeCompare(b.symbol ?? ""),
        ),
    [holdings.data, filter],
  );
  return (
    <main className="shell workspace-shell portfolio-shell">
      <WorkspaceNav />
      <section className="workspace-heading portfolio-heading">
        <div>
          <p className="eyebrow">PORTFOLIO INVENTORY</p>
          <h1>我的持仓</h1>
        </div>
        <a className="market-link" href="/portfolio/import">
          导入或更新持仓 →
        </a>
      </section>
      {holdings.isError ? (
        <section className="context-card" role="alert">
          <h2>无法加载持仓</h2>
          <p>请确认已经登录。系统不会用示例数据替代真实持仓。</p>
        </section>
      ) : holdings.isPending ? (
        <section className="context-card">正在加载持仓…</section>
      ) : !holdings.data.length ? (
        <section className="context-card">
          <h2>尚未导入投资组合</h2>
          <p>导入并确认持仓后，这里会显示真实资产及分析状态。</p>
          <a href="/portfolio/import">开始导入</a>
        </section>
      ) : (
        <>
          <div className="portfolio-filters" role="group" aria-label="持仓筛选">
            {filters.map((item) => (
              <button
                className={filter === item ? "active" : ""}
                key={item}
                onClick={() => {
                  setFilter(item);
                }}
              >
                {item}
              </button>
            ))}
          </div>
          <section className="holdings-table-wrap">
            <table className="holdings-table">
              <thead>
                <tr>
                  <th>代码</th>
                  <th>行动</th>
                  <th>当前</th>
                  <th>目标</th>
                  <th>公司质量</th>
                  <th>估值</th>
                  <th>修正</th>
                  <th>趋势</th>
                  <th>事件</th>
                  <th>风险</th>
                  <th>数据</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id} data-priority={row.priority}>
                    <td>
                      <a href={`/positions/${String(row.id)}`}>
                        <strong>{row.symbol}</strong>
                        <span>{row.name}</span>
                      </a>
                      {!row.classificationConfirmed ? (
                        <div className="classification-required">
                          <small>组合角色待确认</small>
                          <button
                            onClick={() => {
                              setClassificationTarget(row);
                            }}
                          >
                            确认分类
                          </button>
                        </div>
                      ) : (
                        <small>
                          {presentClassification(row.classification)}
                        </small>
                      )}
                    </td>
                    <td>
                      <span
                        className={`priority-pill ${presentPriority(row.priority).tone}`}
                      >
                        {presentAction(row.action).shortTitle}
                      </span>
                    </td>
                    <td>
                      {formatPercent(row.currentWeight)}
                      <small>{formatMoney(row.marketValue, 2)}</small>
                    </td>
                    <td>{target(row)}</td>
                    <td>打开报告</td>
                    <td>打开报告</td>
                    <td>打开报告</td>
                    <td>打开报告</td>
                    <td>{formatDate(row.nextEvent)}</td>
                    <td>{presentConfidence(row.confidence).label}</td>
                    <td>{presentReadiness(row.dataStatus).label}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!rows.length ? (
              <p className="empty-state">此筛选条件下没有持仓。</p>
            ) : null}
          </section>
        </>
      )}
      {classificationTarget?.id && classificationTarget.version != null ? (
        <ClassificationModal
          positionId={classificationTarget.id}
          version={classificationTarget.version}
          onClose={() => {
            setClassificationTarget(undefined);
          }}
        />
      ) : null}
      <footer>
        <span>按行动优先级排序</span>
        <span>分类需逐项确认</span>
        <span>不会自动交易</span>
      </footer>
    </main>
  );
}
