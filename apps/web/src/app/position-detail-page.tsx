import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router";
import { getJson } from "./http";
import { PositionChart, type PositionChartData } from "./position-chart";
import { WorkspaceNav } from "./workspace-nav";

type PositionReport = {
  position: {
    id: string;
    symbol: string;
    classification: string;
    classificationSource: string;
  };
  readiness: string;
  recommendation: {
    id?: string | null;
    action?: string | null;
    priority?: string | null;
    quantityMin?: string | null;
    quantityMax?: string | null;
    currentWeight?: string | null;
    targetWeightMin?: string | null;
    targetWeightMax?: string | null;
    confidence?: string | null;
    reasons: string[];
    risks: string[];
    changeConditions: string[];
    winningRule?: string | null;
    resolutionReason?: string | null;
    validUntil?: string | null;
  };
  evidence: {
    analysisStatus: string;
    exactQuantityAllowed: boolean;
    ruleIds: string[];
    strategyVersion?: string | null;
    configHash?: string | null;
  };
  dataAsOf?: string | null;
};
type JournalEntry = {
  id: string;
  entryType: string;
  taxStatus?: string | null;
  realizedR?: string | null;
  mfeR?: string | null;
  maeR?: string | null;
  exitReason?: string | null;
  createdAt?: string | null;
};
function pct(value?: string | null) {
  return value == null ? "—" : `${(Number(value) * 100).toFixed(1)}%`;
}
function quantity(report: PositionReport) {
  const r = report.recommendation;
  if (
    !report.evidence.exactQuantityAllowed ||
    (!r.quantityMin && !r.quantityMax)
  )
    return "当前无需交易或证据不足，未提供精确数量";
  return r.quantityMin === r.quantityMax
    ? `${r.quantityMin ?? r.quantityMax ?? "—"} 股`
    : `${r.quantityMin ?? "—"}–${r.quantityMax ?? "—"} 股`;
}

export function PositionDetailPage() {
  const { positionId = "" } = useParams();
  const report = useQuery({
    queryKey: ["position-report", positionId],
    queryFn: () =>
      getJson<PositionReport>(`/api/v1/positions/${positionId}/report`),
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
  const journal = useQuery({
    queryKey: ["position-journal", positionId],
    queryFn: () =>
      getJson<JournalEntry[]>(`/api/v1/positions/${positionId}/journal`),
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
          <p>
            请确认已经登录，且该持仓已完成分析。系统不会用示例结论填充报告。
          </p>
          <a href="/portfolio">返回我的持仓</a>
        </section>
      </main>
    );
  const data = report.data;
  const r = data.recommendation;
  return (
    <main className="shell workspace-shell position-detail-shell">
      <WorkspaceNav />
      <a className="back-link" href="/portfolio">
        ← 返回我的持仓
      </a>
      <section className="position-hero">
        <div>
          <p className="eyebrow">持仓分析</p>
          <h1>{data.position.symbol}</h1>
          <span>{data.position.classification}</span>
        </div>
        <div className="position-verdict">
          <h2>建议：{r.action ?? "等待数据"}</h2>
          <p>置信度：{r.confidence ?? "WAIT_FOR_DATA"}</p>
          <p>
            当前仓位：{pct(r.currentWeight)} · 目标：{pct(r.targetWeightMin)}–
            {pct(r.targetWeightMax)}
          </p>
          <p>建议数量：{quantity(data)}</p>
          <p>
            有效期：
            {r.validUntil
              ? new Date(r.validUntil).toLocaleString("zh-CN")
              : "等待有效期证据"}
          </p>
        </div>
      </section>
      <div className="position-modules">
        <section className="context-card">
          <span className="module-number">1</span>
          <h2>最终结论</h2>
          <p>
            {r.resolutionReason ?? r.reasons[0] ?? "分析尚未形成完整结论。"}
          </p>
          <ul>
            {r.reasons.slice(0, 3).map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>
        <section className="context-card">
          <span className="module-number">2</span>
          <h2>组合中的角色</h2>
          <dl className="module-metrics">
            <div>
              <dt>资产分类</dt>
              <dd>{data.position.classification}</dd>
            </div>
            <div>
              <dt>当前权重</dt>
              <dd>{pct(r.currentWeight)}</dd>
            </div>
            <div>
              <dt>目标区间</dt>
              <dd>
                {pct(r.targetWeightMin)}–{pct(r.targetWeightMax)}
              </dd>
            </div>
          </dl>
        </section>
        <section className="context-card">
          <span className="module-number">3</span>
          <h2>公司质量</h2>
          <p>
            {data.position.classification.includes("ETF")
              ? "该资产按 ETF 证据路径分析，不套用单公司质量模型。"
              : (r.reasons.find((item) =>
                  /quality|fundamental|质量|现金流|增长/i.test(item),
                ) ?? "当前报告没有足够的公司质量明细。")}
          </p>
        </section>
        <section className="context-card">
          <span className="module-number">4</span>
          <h2>估值</h2>
          <p>
            {r.reasons.find((item) => /valuation|估值|折价/i.test(item)) ??
              "当前报告没有足够的估值证据。"}
          </p>
        </section>
        <section className="context-card chart-module">
          <span className="module-number">5</span>
          <h2>价格趋势</h2>
          {chart.isPending ? (
            <p>正在加载真实日线…</p>
          ) : chart.isError ? (
            <div className="chart-empty">无法读取图表数据。</div>
          ) : (
            <PositionChart data={chart.data} />
          )}
        </section>
        <section className="context-card">
          <span className="module-number">6</span>
          <h2>风险和 Stops</h2>
          {r.risks.length ? (
            <ul>
              {r.risks.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          ) : (
            <p>暂无完整风险证据。</p>
          )}
          <p>
            风险线只在完整收盘数据后确认；数据不完整时不会给出精确交易数量。
          </p>
        </section>
        <section className="context-card">
          <span className="module-number">7</span>
          <h2>Thesis</h2>
          <p>
            {r.changeConditions.length
              ? `建议改变条件：${r.changeConditions.join("；")}`
              : "尚无结构化 Thesis 变化条件。"}
          </p>
        </section>
        <section className="context-card">
          <span className="module-number">8</span>
          <h2>财报 / 事件</h2>
          {chart.data?.earningsMarkers.length ? (
            <ul>
              {chart.data.earningsMarkers.map((item) => (
                <li key={`${item.marketDate}-${item.label}`}>
                  {item.marketDate} · {item.label}
                </li>
              ))}
            </ul>
          ) : (
            <p>当前时间范围内没有可靠的财报或事件数据。</p>
          )}
        </section>
        <section className="context-card">
          <span className="module-number">9</span>
          <h2>Cluster overlap</h2>
          <p>
            {r.risks.find((item) => /cluster|overlap|集中|重叠/i.test(item)) ??
              "当前报告没有单独的重叠风险明细。"}
          </p>
        </section>
        <section className="context-card">
          <span className="module-number">10</span>
          <h2>Tax lots</h2>
          <p>当前没有可核验的税务批次明细；系统不会估算或补造成本批次。</p>
        </section>
        <section className="context-card">
          <span className="module-number">11</span>
          <h2>数据来源和时效</h2>
          <dl className="module-metrics">
            <div>
              <dt>分析状态</dt>
              <dd>{data.evidence.analysisStatus}</dd>
            </div>
            <div>
              <dt>就绪状态</dt>
              <dd>{data.readiness}</dd>
            </div>
            <div>
              <dt>数据截至</dt>
              <dd>
                {data.dataAsOf
                  ? new Date(data.dataAsOf).toLocaleString("zh-CN")
                  : "—"}
              </dd>
            </div>
          </dl>
          <details>
            <summary>审计依据</summary>
            <p>策略版本：{data.evidence.strategyVersion ?? "—"}</p>
            <p>规则：{data.evidence.ruleIds.join("、") || "—"}</p>
            <p>配置摘要：{data.evidence.configHash ?? "—"}</p>
          </details>
        </section>
        <section className="context-card">
          <span className="module-number">12</span>
          <h2>决策历史</h2>
          {journal.isPending ? (
            <p>正在加载…</p>
          ) : journal.data?.length ? (
            journal.data.map((item) => (
              <article className="journal-row" key={item.id}>
                <strong>{item.entryType}</strong>
                <span>{item.taxStatus ?? "税务状态未知"}</span>
                <small>
                  已实现 {item.realizedR ?? "—"}R · MFE {item.mfeR ?? "—"}R ·
                  MAE {item.maeR ?? "—"}R · {item.exitReason ?? "持有中"}
                </small>
              </article>
            ))
          ) : (
            <p>暂无可核验的决策或交易历史。</p>
          )}
        </section>
      </div>
      <footer>
        <span>仅供决策支持</span>
        <span>风险优先于税务影响</span>
        <span>不会自动交易</span>
      </footer>
    </main>
  );
}
