import type { components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router";
import { PositionChart, type PositionChartData } from "./position-chart";
import { WorkspaceNav } from "./workspace-nav";
import { getJson } from "./http";

type Report = components["schemas"]["PositionReportResponse"];
type Intelligence = components["schemas"]["IntelligenceResponse"];
const labels: Record<string, string> = {
  BUY: "买入",
  ADD: "增持",
  HOLD: "持有",
  TRIM: "减持",
  SELL: "卖出",
  WATCH: "观察",
  WAIT_FOR_DATA: "等待数据",
  DO_NOT_CHASE: "不要追高",
};
function pct(v?: string | null) {
  return v == null ? "—" : `${(Number(v) * 100).toFixed(1)}%`;
}
function quantity(report: Report) {
  const r = report.recommendation;
  if (
    !r ||
    !report.evidence?.exactQuantityAllowed ||
    (!r.quantityMin && !r.quantityMax)
  )
    return "当前无需交易或证据不足，未提供精确数量";
  return r.quantityMin === r.quantityMax
    ? `${String(r.quantityMin ?? r.quantityMax)} 股`
    : `${r.quantityMin ?? "—"}–${r.quantityMax ?? "—"} 股`;
}
function Tile({
  label,
  value,
  detail,
}: {
  label: string;
  value: string | null | undefined;
  detail?: string;
}) {
  return (
    <article>
      <span>{label}</span>
      <strong>{value ?? "MISSING"}</strong>
      {detail ? <small>{detail}</small> : null}
    </article>
  );
}
export function PositionDetailPage() {
  const { positionId = "" } = useParams();
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
    queryKey: ["position-chart", positionId, "1Y"],
    queryFn: () =>
      getJson<PositionChartData>(
        `/api/v1/positions/${positionId}/chart?range=1Y`,
      ),
    enabled: Boolean(positionId),
    retry: false,
  });
  if (report.isPending)
    return (
      <main className="shell workspace-shell">
        <WorkspaceNav />
        <section className="context-card">正在加载持仓分析…</section>
      </main>
    );
  if (report.isError)
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
  const data = report.data;
  const r = data.recommendation;
  const asset = data.assetEvidence;
  const company = asset?.company;
  const valuation = intelligence.data?.valuation;
  const position = data.position;
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
  )
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
  return (
    <main className="shell workspace-shell position-detail-shell">
      <WorkspaceNav />
      <a className="back-link" href="/portfolio">
        ← 返回我的持仓
      </a>
      <section className="position-hero">
        <div>
          <p className="eyebrow">POSITION REPORT</p>
          <h1>{position?.symbol ?? "—"}</h1>
          <span>{position?.classification ?? "分类待确认"}</span>
        </div>
        <div className="position-verdict">
          <h2>
            {labels[r?.action ?? ""] ?? r?.action ?? "等待数据"}{" "}
            <small>{r?.action}</small>
          </h2>
          <p className="analyst-line">
            {r?.resolutionReason ??
              r?.reasons?.[0] ??
              "分析证据尚未形成完整结论。"}
          </p>
          <p>
            置信度 {r?.confidence ?? "WAIT_FOR_DATA"} · 当前{" "}
            {pct(r?.currentWeight)} · 目标 {pct(r?.targetWeightMin)}–
            {pct(r?.targetWeightMax)}
          </p>
          <p>
            建议数量 {quantity(data)} · 常规上限 {pct(r?.targetWeightMax)} ·
            硬上限 {pct(asset?.speculative?.hardMaxWeight)}
          </p>
        </div>
      </section>
      <section className="evidence-tiles">
        <Tile
          label="公司质量"
          value={
            company?.growthProfitabilityCashFlowStatus ??
            (asset?.etf ? "ETF MODEL" : "MISSING")
          }
        />
        <Tile
          label="估值"
          value={company?.valuationStatus ?? valuation?.action}
        />
        <Tile label="预期修正" value={valuation?.earningsRevisions} />
        <Tile
          label="趋势"
          value={asset?.etf?.trendStatus ?? valuation?.priceStabilization}
        />
        <Tile
          label="组合容量"
          value={
            valuation?.portfolioCapacity === true
              ? "AVAILABLE"
              : valuation?.portfolioCapacity === false
                ? "CONSTRAINED"
                : "MISSING"
          }
          detail={`当前 ${pct(asset?.portfolioContext?.currentWeight)}`}
        />
      </section>
      <div className="position-modules analyst-six-layers">
        <section className="context-card">
          <span className="module-number">01</span>
          <h2>系统建议</h2>
          <p className="analyst-line">
            {labels[layers.systemRecommendation.action ?? ""] ??
              layers.systemRecommendation.action}
          </p>
          <dl className="financial-grid">
            <div>
              <dt>优先级</dt>
              <dd>{layers.systemRecommendation.priority ?? "—"}</dd>
            </div>
            <div>
              <dt>置信度</dt>
              <dd>{layers.systemRecommendation.confidence ?? "—"}</dd>
            </div>
            <div>
              <dt>建议数量</dt>
              <dd>{quantity(data)}</dd>
            </div>
            <div>
              <dt>精确数量可用</dt>
              <dd>
                {layers.systemRecommendation.exactQuantityAllowed ? "是" : "否"}
              </dd>
            </div>
          </dl>
        </section>
        <section className="context-card">
          <span className="module-number">02</span>
          <h2>组合中的角色</h2>
          <dl className="financial-grid">
            <div>
              <dt>分类</dt>
              <dd>{layers.portfolioRole.classification ?? "待确认"}</dd>
            </div>
            <div>
              <dt>当前仓位</dt>
              <dd>{pct(layers.portfolioRole.currentWeight)}</dd>
            </div>
            <div>
              <dt>目标仓位</dt>
              <dd>
                {pct(layers.portfolioRole.targetWeightMin)}–
                {pct(layers.portfolioRole.targetWeightMax)}
              </dd>
            </div>
            <div>
              <dt>Hard Max</dt>
              <dd>{pct(layers.portfolioRole.hardMaxWeight)}</dd>
            </div>
          </dl>
          <p>{layers.portfolioRole.capacityExplanation}</p>
        </section>
        <section className="context-card">
          <span className="module-number">03</span>
          <h2>基本面</h2>
          <p>
            <strong>财务健康：</strong>
            {layers.fundamentals.financialHealth ?? "数据不足"}
          </p>
          <p>
            <strong>证据质量：</strong>
            {layers.fundamentals.quality ?? "MISSING"}
          </p>
          <p>
            {layers.fundamentals.available
              ? "基本面证据已纳入本次确定性分析。"
              : "基本面证据不完整，系统不会猜测缺失数字。"}
          </p>
        </section>
        <section className="context-card">
          <span className="module-number">04</span>
          <h2>估值</h2>
          <p>
            <strong>{layers.valuation.state ?? "数据不足"}</strong> · 置信度{" "}
            {layers.valuation.confidence ?? "MISSING"}
          </p>
          <p>
            {layers.valuation.attractiveButCannotAdd
              ? "估值可能有吸引力，但组合仓位已触及上限，因此现在不能继续加仓。"
              : layers.valuation.attractive
                ? "估值有吸引力，但仍须通过组合容量与风险约束。"
                : "当前估值证据不足以单独支持加仓。"}
          </p>
          <p>
            独立确认：
            {layers.valuation.independentConfirmation ? "已确认" : "未确认"}
          </p>
        </section>
        <section className="context-card chart-module">
          <span className="module-number">05</span>
          <h2>价格 / 风险 / 财报</h2>
          <dl className="module-metrics">
            <div>
              <dt>价格状态</dt>
              <dd>{layers.priceRiskEarnings.priceState ?? "MISSING"}</dd>
            </div>
            <div>
              <dt>正式止损</dt>
              <dd>{layers.priceRiskEarnings.formalStop ?? "—"}</dd>
            </div>
            <div>
              <dt>动态止损</dt>
              <dd>{layers.priceRiskEarnings.liveStop ?? "—"}</dd>
            </div>
            <div>
              <dt>财报风险</dt>
              <dd>{layers.priceRiskEarnings.earningsRisk ?? "MISSING"}</dd>
            </div>
          </dl>
          {chart.isPending ? (
            <p>正在加载真实日线…</p>
          ) : chart.isError ? (
            <div className="chart-empty">无法读取图表数据。</div>
          ) : (
            <PositionChart data={chart.data} />
          )}
        </section>
        <section className="context-card chart-module">
          <span className="module-number">06</span>
          <h2>为什么、风险与失效条件</h2>
          <div className="action-evidence">
            <section>
              <h3>为什么</h3>
              <ul>
                {(rationale.reasons ?? []).map((x) => (
                  <li key={x}>{x}</li>
                ))}
              </ul>
            </section>
            <section>
              <h3>最大风险</h3>
              <ul>
                {(rationale.risks ?? []).map((x) => (
                  <li key={x}>{x}</li>
                ))}
              </ul>
            </section>
          </div>
          <h3>什么情况下建议会改变</h3>
          <p>
            {(rationale.changeConditions ?? []).join("；") || "暂无明确条件"}
          </p>
          <details className="evidence-drawer">
            <summary>查看证据与审计信息</summary>
            <p>
              策略版本：
              {drawer.strategyVersion ?? "—"}
            </p>
            <p>
              规则：
              {(drawer.ruleIds ?? []).join(", ") || "—"}
            </p>
            <p>
              证据引用：
              {(drawer.evidenceRefs ?? []).join(", ") || "—"}
            </p>
            <p>
              数据质量：
              {drawer.dataQuality ?? "MISSING"}
            </p>
            <p>
              配置哈希：
              {drawer.configHash ?? "—"}
            </p>
          </details>
        </section>
      </div>
      <footer>
        <span>仅供决策支持</span>
        <span>证据缺失时不显示精确数量</span>
        <span>不会自动交易</span>
      </footer>
    </main>
  );
}
