import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ClassificationModal } from "./classification-modal";
import { getJson } from "./http";
import { WorkspaceNav } from "./workspace-nav";

export type PortfolioHolding = {
  id: string;
  version: number;
  symbol: string;
  name: string;
  assetType: string;
  bucket: string;
  classification: string;
  classificationConfirmed: boolean;
  marketValue: string;
  currentWeight: string;
  targetWeightMin?: string | null;
  targetWeightMax?: string | null;
  action: string;
  priority: string;
  confidence: string;
  trend: string;
  nextEvent?: string | null;
  dataStatus: string;
};

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
const priority = new Map([
  ["MUST_ACT", 0],
  ["DO_NOT", 1],
  ["WATCH", 2],
  ["NORMAL", 3],
]);
function pct(value?: string | null) {
  return value == null ? "—" : `${(Number(value) * 100).toFixed(1)}%`;
}
function money(value: string) {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "USD",
  }).format(Number(value));
}
function target(row: PortfolioHolding) {
  return row.targetWeightMin == null && row.targetWeightMax == null
    ? "—"
    : `${pct(row.targetWeightMin)}–${pct(row.targetWeightMax)}`;
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
    return !["READY", "HEALTHY"].includes(row.dataStatus);
  if (filter === "即将财报")
    return Boolean(
      row.nextEvent &&
      new Date(row.nextEvent).getTime() < Date.now() + 30 * 86_400_000,
    );
  if (filter === "Core")
    return row.bucket === "CORE" || row.classification.startsWith("CORE_");
  if (filter === "Tactical")
    return (
      row.bucket === "TACTICAL" ||
      row.classification.includes("TACTICAL") ||
      row.classification === "THEMATIC_ETF"
    );
  return row.classification === "SPECULATIVE";
}

export function PortfolioPage() {
  const holdings = useQuery({
    queryKey: ["portfolio-holdings"],
    queryFn: () => getJson<PortfolioHolding[]>("/api/v1/portfolio/holdings"),
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
            (priority.get(a.priority) ?? 4) - (priority.get(b.priority) ?? 4) ||
            a.symbol.localeCompare(b.symbol),
        ),
    [holdings.data, filter],
  );
  return (
    <main className="shell workspace-shell portfolio-shell">
      <WorkspaceNav />
      <section className="workspace-heading portfolio-heading">
        <div>
          <p className="eyebrow">组合中的全部资产</p>
          <h1>我的持仓</h1>
        </div>
        <a className="market-link" href="/portfolio/import">
          导入或更新持仓
        </a>
      </section>
      {holdings.isError ? (
        <section className="context-card" role="alert">
          <h2>无法加载持仓</h2>
          <p>请确认已经登录，或稍后重试。系统不会用示例数据代替真实持仓。</p>
        </section>
      ) : holdings.isPending ? (
        <section className="context-card">正在加载持仓…</section>
      ) : !holdings.data.length ? (
        <section className="context-card">
          <h2>尚未导入投资组合</h2>
          <p>导入并确认持仓后，这里会显示真实资产及其分析状态。</p>
          <a href="/portfolio/import">开始导入</a>
        </section>
      ) : (
        <>
          <div className="portfolio-filters" role="group" aria-label="持仓筛选">
            {filters.map((item) => (
              <button
                className={filter === item ? "active" : ""}
                key={item}
                onClick={() => { setFilter(item); }}
              >
                {item}
              </button>
            ))}
          </div>
          <section className="holdings-table-wrap">
            <table className="holdings-table">
              <thead>
                <tr>
                  <th>代码 / 名称</th>
                  <th>类型</th>
                  <th>当前市值</th>
                  <th>当前权重</th>
                  <th>目标区间</th>
                  <th>建议</th>
                  <th>置信度</th>
                  <th>趋势</th>
                  <th>下一事件</th>
                  <th>数据状态</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id} data-priority={row.priority}>
                    <td>
                      <a href={`/positions/${row.id}`}>
                        <strong>{row.symbol}</strong>
                        <span>{row.name}</span>
                      </a>
                      {!row.classificationConfirmed ? (
                        <div className="classification-required">
                          <em>未分类</em>
                          <button onClick={() => { setClassificationTarget(row); }}>
                            查看系统建议
                          </button>
                        </div>
                      ) : null}
                    </td>
                    <td>
                      {row.assetType}
                      <small>
                        {row.classificationConfirmed
                          ? row.classification
                          : "待确认"}
                      </small>
                    </td>
                    <td>{money(row.marketValue)}</td>
                    <td>{pct(row.currentWeight)}</td>
                    <td>{target(row)}</td>
                    <td>
                      <span
                        className={`priority-pill ${row.priority.toLowerCase()}`}
                      >
                        {row.action}
                      </span>
                    </td>
                    <td>{row.confidence}</td>
                    <td>{row.trend}</td>
                    <td>
                      {row.nextEvent
                        ? new Date(row.nextEvent).toLocaleDateString("zh-CN")
                        : "—"}
                    </td>
                    <td>{row.dataStatus}</td>
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
      {classificationTarget ? (
        <ClassificationModal
          positionId={classificationTarget.id}
          version={classificationTarget.version}
          onClose={() => { setClassificationTarget(undefined); }}
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
