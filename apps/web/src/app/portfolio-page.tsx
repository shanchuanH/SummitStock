import type { components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import { ClassificationModal } from "./classification-modal";
import { getJson } from "./http";
import { HoldingCard } from "./portfolio/HoldingCard";
import { formatMoney } from "./presentation/number-format";
import { WorkspaceNav } from "./workspace-nav";

export type PortfolioHolding =
  components["schemas"]["PortfolioHoldingResponse"];
type ExecutiveBrief = components["schemas"]["ExecutiveBrief"];

const filters = [
  "全部",
  "今天要处理",
  "现在不要加",
  "即将财报",
  "数据待补",
  "核心仓",
  "主动仓",
  "投机仓",
] as const;
type Filter = (typeof filters)[number];

const priorityRank: Record<string, number> = {
  MUST_ACT: 0,
  DO_NOT: 1,
  WATCH: 2,
  NORMAL: 3,
};

function matchesFilter(row: PortfolioHolding, filter: Filter) {
  if (filter === "全部") return true;
  if (filter === "今天要处理") return row.priority === "MUST_ACT";
  if (filter === "现在不要加") return row.priority === "DO_NOT";
  if (filter === "数据待补")
    return !["READY", "HEALTHY"].includes(row.dataStatus ?? "");
  if (filter === "即将财报") {
    return Boolean(
      row.nextEvent &&
      new Date(row.nextEvent).getTime() < Date.now() + 30 * 86_400_000,
    );
  }
  if (filter === "核心仓") {
    return row.bucket === "CORE" || row.classification?.startsWith("CORE_");
  }
  if (filter === "主动仓") {
    return (
      row.bucket === "TACTICAL" ||
      row.classification?.includes("TACTICAL") ||
      row.classification === "THEMATIC_ETF"
    );
  }
  return row.classification === "SPECULATIVE";
}

async function loadHoldings() {
  return getJson<PortfolioHolding[]>("/api/v1/portfolio/holdings");
}

async function loadBrief() {
  return getJson<ExecutiveBrief>("/api/v1/brief/today");
}

export function PortfolioPage() {
  const holdings = useQuery({
    queryKey: ["portfolio-holdings"],
    queryFn: loadHoldings,
    retry: false,
  });
  const brief = useQuery({
    queryKey: ["portfolio-summary"],
    queryFn: loadBrief,
    retry: false,
  });
  const [filter, setFilter] = useState<Filter>("全部");
  const [search, setSearch] = useState("");
  const [classificationTarget, setClassificationTarget] =
    useState<PortfolioHolding>();
  const rows = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    return [...(holdings.data ?? [])]
      .filter((row) => matchesFilter(row, filter))
      .filter(
        (row) =>
          !term ||
          row.symbol?.toLocaleLowerCase().includes(term) ||
          row.name?.toLocaleLowerCase().includes(term),
      )
      .sort(
        (a, b) =>
          (priorityRank[a.priority ?? ""] ?? 4) -
            (priorityRank[b.priority ?? ""] ?? 4) ||
          (a.symbol ?? "").localeCompare(b.symbol ?? ""),
      );
  }, [holdings.data, filter, search]);

  return (
    <main className="shell workspace-shell portfolio-shell">
      <WorkspaceNav />
      <section className="workspace-heading portfolio-heading">
        <div>
          <p className="eyebrow">你的投资组合</p>
          <h1>我的持仓</h1>
        </div>
        <a className="market-link" href="/portfolio/import">
          导入或更新持仓 →
        </a>
      </section>
      <section className="portfolio-summary-strip" aria-label="组合摘要">
        <article>
          <span>总投资资产</span>
          <strong>{formatMoney(brief.data?.summary.investedValue)}</strong>
        </article>
        <article>
          <span>可部署现金</span>
          <strong>{formatMoney(brief.data?.capital.deployableCash)}</strong>
        </article>
        <article>
          <span>生活备用金</span>
          <strong>{formatMoney(brief.data?.capital.emergencyReserve)}</strong>
        </article>
        <article>
          <span>今日需处理</span>
          <strong>{brief.data?.mustAct.length ?? "—"}</strong>
        </article>
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
          <div className="portfolio-tools">
            <label className="portfolio-search">
              <Search aria-hidden="true" />
              <span className="sr-only">搜索持仓</span>
              <input
                type="search"
                placeholder="搜索代码或公司名称"
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value);
                }}
              />
            </label>
            <div
              className="portfolio-filters"
              role="group"
              aria-label="持仓筛选"
            >
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
          </div>
          <section className="holdings-table-wrap">
            <table className="holdings-table">
              <thead>
                <tr>
                  <th>持仓</th>
                  <th>系统建议</th>
                  <th>仓位</th>
                  <th>关键原因</th>
                  <th>下一个事件</th>
                  <th>数据状态</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <HoldingCard
                    key={row.id}
                    holding={row}
                    onConfirmClassification={setClassificationTarget}
                  />
                ))}
              </tbody>
            </table>
            {!rows.length ? (
              <p className="empty-state">没有符合当前条件的持仓。</p>
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
