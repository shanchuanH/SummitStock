import type { components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useParams } from "react-router";
import { getJson } from "./http";
import { PositionChart, type PositionChartData } from "./position-chart";
import { presentAction } from "./presentation/action-presentation";
import { presentClassification } from "./presentation/classification-presentation";
import { presentConfidence } from "./presentation/confidence-presentation";
import {
  formatMoney,
  formatPercent,
  formatQuantity,
} from "./presentation/number-format";
import { presentPriority } from "./presentation/priority-presentation";
import { presentReadiness } from "./presentation/readiness-presentation";
import { WorkspaceNav } from "./workspace-nav";

type Report = components["schemas"]["PositionReportResponse"];
type Intelligence = components["schemas"]["IntelligenceResponse"];
type ChartRange = "3M" | "6M" | "1Y" | "3Y";

const unavailable = "暂无可靠数据";

function quantity(report: Report) {
  const recommendation = report.recommendation;
  if (
    !recommendation ||
    !report.evidence?.exactQuantityAllowed ||
    (!recommendation.quantityMin && !recommendation.quantityMax)
  ) {
    return "当前无需交易或证据不足，未提供精确数量";
  }
  return (
    formatQuantity(recommendation.quantityMin, recommendation.quantityMax) ??
    "当前暂无精确数量"
  );
}

function formatDecimal(value?: string | null, suffix = "") {
  if (value == null || value === "") return unavailable;
  const number = Number(value);
  return Number.isFinite(number)
    ? `${new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(number)}${suffix}`
    : unavailable;
}

function formatCompactMoney(value?: string | null) {
  if (value == null || value === "") return unavailable;
  const number = Number(value);
  return Number.isFinite(number)
    ? new Intl.NumberFormat("zh-CN", {
        style: "currency",
        currency: "USD",
        notation: "compact",
        maximumFractionDigits: 2,
      }).format(number)
    : unavailable;
}

function formatRevision(status?: string | null, change?: string | null) {
  const labels: Record<string, string> = {
    STRONGLY_POSITIVE: "显著上调",
    POSITIVE: "上调",
    FLAT: "基本不变",
    NEGATIVE: "下调",
    STRONGLY_NEGATIVE: "显著下调",
  };
  if (!status || status === "MISSING") return unavailable;
  const changeText = change == null ? "" : `（EPS ${formatPercent(change)}）`;
  return `${labels[status] ?? "状态待复核"}${changeText}`;
}

function presentEvidenceValue(value?: string | null) {
  const labels: Record<string, string> = {
    STRONG: "强劲",
    HEALTHY: "健康",
    STABLE: "稳定",
    WEAKENING: "转弱",
    BROKEN: "已破坏",
    UPTREND: "上升趋势",
    DOWNTREND: "下降趋势",
    SIDEWAYS: "横盘",
    ELEVATED: "风险升高",
    NORMAL: "正常",
    HIGH: "高",
    LOW: "低",
  };
  if (!value || value === "MISSING") return unavailable;
  return labels[value] ?? "状态待复核";
}

function valuationSentence(
  state?: string | null,
  attractiveButCannotAdd?: boolean,
) {
  const states: Record<string, string> = {
    DEEP_DISCOUNT: "估值处于历史显著偏低区间",
    ATTRACTIVE: "估值处于历史偏低区间",
    FAIR: "估值处于历史中间区间",
    RICH: "估值处于历史偏高区间",
    EXTREME: "估值处于历史极高区间",
  };
  if (!state || state === "MISSING")
    return "估值数据不足，不能据此判断贵或便宜。";
  const base = states[state] ?? "估值状态需要复核";
  return attractiveButCannotAdd
    ? `${base}，但还不能忽略当前仓位上限。`
    : `${base}，是否操作仍由组合容量与风险约束共同决定。`;
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

export function PositionDetailPage() {
  const { positionId = "" } = useParams();
  const [chartRange, setChartRange] = useState<ChartRange>("1Y");
  const report = useQuery({
    queryKey: ["position-report", positionId],
    queryFn: () =>
      getJson<Report>(`/api/v1/holdings/${positionId}/analyst-report`),
    enabled: Boolean(positionId),
    retry: false,
  });
  const intelligence = useQuery({
    queryKey: ["position-intelligence", positionId],
    queryFn: () =>
      getJson<Intelligence>(`/api/v1/positions/${positionId}/intelligence`),
    enabled: Boolean(positionId),
    retry: false,
  });
  const chart = useQuery({
    queryKey: ["position-chart", positionId, chartRange],
    queryFn: () =>
      getJson<PositionChartData>(
        `/api/v1/positions/${positionId}/chart?range=${chartRange}`,
      ),
    enabled: Boolean(positionId),
    retry: false,
  });

  if (report.isPending) {
    return (
      <main className="shell workspace-shell">
        <WorkspaceNav />
        <section className="context-card">正在加载持仓分析…</section>
      </main>
    );
  }
  if (report.isError) {
    return (
      <main className="shell workspace-shell">
        <WorkspaceNav />
        <section className="context-card" role="alert">
          <h1>持仓分析尚不可用</h1>
          <p>系统不会用示例结论填充报告。</p>
          <a href="/portfolio">返回我的持仓</a>
        </section>
      </main>
    );
  }

  const data = report.data;
  const recommendation = data.recommendation;
  const layers = data.layers;
  const rationale = layers?.rationaleAndEvidence;
  const drawer = rationale?.evidenceDrawer;
  if (
    !layers?.systemRecommendation ||
    !layers.portfolioRole ||
    !layers.fundamentals ||
    !layers.valuation ||
    !layers.priceRiskEarnings ||
    !rationale ||
    !drawer
  ) {
    return (
      <main className="shell workspace-shell">
        <WorkspaceNav />
        <section className="context-card" role="alert">
          <h1>持仓分析尚不可用</h1>
          <p>六层分析证据不完整，系统不会显示拼凑的报告。</p>
          <a href="/portfolio">返回我的持仓</a>
        </section>
      </main>
    );
  }

  const isCompany = Boolean(data.assetEvidence?.company?.companyModelApplied);
  const dataLimited = isCompany && !layers.fundamentals.available;
  const action = presentAction(recommendation?.action);
  const investmentCase =
    recommendation?.narrative?.oneSentence ??
    recommendation?.resolutionReason ??
    recommendation?.reasons?.[0] ??
    "可靠证据不足，暂时无法概括投资逻辑。";
  const why = dataLimited
    ? "最新财务数据尚未形成可靠证据；当前持仓不会因此被自动视为安全。"
    : (recommendation?.resolutionReason ??
      recommendation?.reasons?.[0] ??
      "分析证据尚未形成完整解释。");
  const todayAction = dataLimited
    ? "等待可靠数据完成后再判断，不新增交易。"
    : layers.systemRecommendation.exactQuantityAllowed
      ? `${action.title}：${quantity(data)}`
      : action.title;
  const fundamentals = intelligence.data?.fundamentalMetrics;
  const valuationMetrics = intelligence.data?.valuationMetrics;

  return (
    <main className="shell workspace-shell position-detail-shell">
      <WorkspaceNav />
      <a className="back-link" href="/portfolio">
        ← 返回我的持仓
      </a>
      <section className="position-hero">
        <div>
          <p className="eyebrow">持仓决策</p>
          <h1>{data.position?.symbol ?? "—"}</h1>
          <span>{presentClassification(data.position?.classification)}</span>
        </div>
        <div className="position-verdict">
          <h2>{dataLimited ? "暂不提供新的交易建议" : action.title}</h2>
          <dl className="position-weight-summary">
            <Metric
              label="当前仓位"
              value={formatPercent(layers.portfolioRole.currentWeight)}
            />
            <Metric
              label="正常上限"
              value={formatPercent(layers.portfolioRole.normalMaxWeight)}
            />
            <Metric
              label="硬上限"
              value={formatPercent(layers.portfolioRole.hardMaxWeight)}
            />
          </dl>
          <h3>为什么</h3>
          <p className="analyst-line">{why}</p>
          <h3>今天要做</h3>
          <p>{todayAction}</p>
          <p>
            置信度：{presentConfidence(recommendation?.confidence).label} ·
            数据更新：
            {data.dataAsOf
              ? new Date(data.dataAsOf).toLocaleString("zh-CN")
              : unavailable}
          </p>
        </div>
      </section>
      <section className="investment-case" aria-label="投资逻辑">
        <strong>一句话投资逻辑</strong>
        <span>{investmentCase}</span>
      </section>

      <div className="position-modules analyst-six-layers">
        <details className="context-card analyst-layer" open>
          <summary>
            <span className="module-number">01</span>
            <h2>系统建议</h2>
          </summary>
          <p className="analyst-line">
            {dataLimited ? "等待可靠数据" : action.title}
          </p>
          <dl className="financial-grid">
            <Metric
              label="优先级"
              value={
                presentPriority(layers.systemRecommendation.priority).label
              }
            />
            <Metric
              label="置信度"
              value={
                presentConfidence(layers.systemRecommendation.confidence).label
              }
            />
            <Metric label="建议数量" value={quantity(data)} />
            <Metric
              label="精确数量可用"
              value={
                layers.systemRecommendation.exactQuantityAllowed ? "是" : "否"
              }
            />
          </dl>
        </details>

        <details className="context-card analyst-layer" open>
          <summary>
            <span className="module-number">02</span>
            <h2>组合中的角色</h2>
          </summary>
          <dl className="financial-grid">
            <Metric
              label="分类"
              value={presentClassification(layers.portfolioRole.classification)}
            />
            <Metric
              label="当前仓位"
              value={formatPercent(layers.portfolioRole.currentWeight)}
            />
            <Metric
              label="目标仓位"
              value={`${formatPercent(layers.portfolioRole.targetWeightMin)}–${formatPercent(layers.portfolioRole.targetWeightMax)}`}
            />
            <Metric
              label="正常上限"
              value={formatPercent(layers.portfolioRole.normalMaxWeight)}
            />
            <Metric
              label="硬上限"
              value={formatPercent(layers.portfolioRole.hardMaxWeight)}
            />
          </dl>
          <p>{layers.portfolioRole.capacityExplanation}</p>
        </details>

        <details className="context-card analyst-layer">
          <summary>
            <span className="module-number">03</span>
            <h2>公司基本面</h2>
          </summary>
          <p>
            财务健康：
            {presentEvidenceValue(layers.fundamentals.financialHealth)} ·
            证据质量：
            {presentReadiness(layers.fundamentals.quality).label}
          </p>
          <dl className="financial-grid">
            <Metric
              label="Revenue TTM"
              value={formatCompactMoney(fundamentals?.revenueTtm)}
            />
            <Metric
              label="Revenue YoY"
              value={formatPercent(fundamentals?.revenueYoy)}
            />
            <Metric
              label="EPS TTM"
              value={
                fundamentals?.epsTtm == null
                  ? unavailable
                  : formatMoney(fundamentals.epsTtm, 2)
              }
            />
            <Metric
              label="Operating Margin"
              value={formatPercent(fundamentals?.operatingMargin)}
            />
            <Metric
              label="FCF TTM"
              value={formatCompactMoney(fundamentals?.fcfTtm)}
            />
            <Metric
              label="FCF Margin"
              value={formatPercent(fundamentals?.fcfMargin)}
            />
            <Metric
              label="Net Cash / Net Debt"
              value={formatCompactMoney(fundamentals?.netCash)}
            />
            <Metric
              label="Dilution"
              value={formatPercent(fundamentals?.dilutionYoy)}
            />
            <Metric
              label="Estimate Revision 30d"
              value={formatRevision(
                fundamentals?.revision30d,
                fundamentals?.epsChange30d,
              )}
            />
            <Metric
              label="Estimate Revision 90d"
              value={formatRevision(
                fundamentals?.revision90d,
                fundamentals?.epsChange90d,
              )}
            />
          </dl>
        </details>

        <details className="context-card analyst-layer">
          <summary>
            <span className="module-number">04</span>
            <h2>估值</h2>
          </summary>
          <p className="analyst-line">
            {valuationSentence(
              layers.valuation.state,
              layers.valuation.attractiveButCannotAdd,
            )}
          </p>
          <dl className="financial-grid">
            <Metric
              label="P/E TTM"
              value={formatDecimal(valuationMetrics?.trailingPe, "×")}
            />
            <Metric
              label="P/E FY1"
              value={formatDecimal(valuationMetrics?.forwardPe, "×")}
            />
            <Metric
              label="EV / Sales"
              value={formatDecimal(valuationMetrics?.evSales, "×")}
            />
            <Metric
              label="FCF Yield"
              value={formatPercent(valuationMetrics?.fcfYield)}
            />
            <Metric
              label="历史估值 percentile（5 年）"
              value={formatPercent(valuationMetrics?.historyPercentile5y)}
            />
          </dl>
        </details>

        <details className="context-card analyst-layer chart-module">
          <summary>
            <span className="module-number">05</span>
            <h2>价格 / 风险 / 财报</h2>
          </summary>
          <dl className="module-metrics">
            <Metric
              label="价格状态"
              value={presentEvidenceValue(layers.priceRiskEarnings.priceState)}
            />
            <Metric
              label="正式止损"
              value={layers.priceRiskEarnings.formalStop ?? unavailable}
            />
            <Metric
              label="动态止损"
              value={layers.priceRiskEarnings.liveStop ?? unavailable}
            />
            <Metric
              label="财报风险"
              value={presentEvidenceValue(
                layers.priceRiskEarnings.earningsRisk,
              )}
            />
          </dl>
          <div className="chart-range" aria-label="图表范围">
            {(["3M", "6M", "1Y", "3Y"] as const).map((range) => (
              <button
                type="button"
                key={range}
                aria-pressed={chartRange === range}
                onClick={() => {
                  setChartRange(range);
                }}
              >
                {range}
              </button>
            ))}
          </div>
          {chart.isPending ? (
            <p>正在加载真实日线…</p>
          ) : chart.isError ? (
            <div className="chart-empty">无法读取图表数据。</div>
          ) : (
            <PositionChart data={chart.data} />
          )}
        </details>

        <details className="context-card analyst-layer chart-module">
          <summary>
            <span className="module-number">06</span>
            <h2>完整证据</h2>
          </summary>
          <div className="action-evidence">
            <section>
              <h3>为什么</h3>
              <ul>
                {(rationale.reasons ?? []).map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </section>
            <section>
              <h3>最大风险</h3>
              <ul>
                {(rationale.risks ?? []).map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </section>
          </div>
          <h3>什么情况下建议会改变</h3>
          <p>
            {(rationale.changeConditions ?? []).join("；") || "暂无明确条件"}
          </p>
          <div className="evidence-drawer">
            <p>策略版本：{drawer.strategyVersion ?? "—"}</p>
            <p>规则：{(drawer.ruleIds ?? []).join(", ") || "—"}</p>
            <p>证据引用：{(drawer.evidenceRefs ?? []).join(", ") || "—"}</p>
            <p>数据质量：{presentReadiness(drawer.dataQuality).label}</p>
            <p>配置哈希：{drawer.configHash ?? "—"}</p>
          </div>
        </details>
      </div>
      <footer>
        <span>仅供决策支持</span>
        <span>证据缺失时不显示精确数量</span>
        <span>不会自动交易</span>
      </footer>
    </main>
  );
}
