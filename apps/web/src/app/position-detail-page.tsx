import type { components } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router";
import { PositionChart, type PositionChartData } from "./position-chart";
import { WorkspaceNav } from "./workspace-nav";
import { getJson } from "./http";

type Report = components["schemas"]["PositionReportResponse"];
type Intelligence = components["schemas"]["IntelligenceResponse"];
const labels:Record<string,string>={BUY:"买入",ADD:"增持",HOLD:"持有",TRIM:"减持",SELL:"卖出",WATCH:"观察",WAIT_FOR_DATA:"等待数据",DO_NOT_CHASE:"不要追高"};
function pct(v?:string|null){return v==null?"—":`${(Number(v)*100).toFixed(1)}%`;}
function quantity(report:Report){const r=report.recommendation;if(!r||!report.evidence?.exactQuantityAllowed||(!r.quantityMin&&!r.quantityMax))return "当前无需交易或证据不足，未提供精确数量";return r.quantityMin===r.quantityMax?`${String(r.quantityMin??r.quantityMax)} 股`:`${r.quantityMin??"—"}–${r.quantityMax??"—"} 股`;}
function Tile({label,value,detail}:{label:string;value:string|null|undefined;detail?:string}){return <article><span>{label}</span><strong>{value??"MISSING"}</strong>{detail?<small>{detail}</small>:null}</article>;}
export function PositionDetailPage(){
  const {positionId=""}=useParams();
  const report=useQuery({queryKey:["position-report",positionId],queryFn:()=>getJson<Report>(`/api/v1/positions/${positionId}/report`),enabled:Boolean(positionId),retry:false});
  const intelligence=useQuery({queryKey:["position-intelligence",positionId],queryFn:()=>getJson<Intelligence>(`/api/v1/positions/${positionId}/intelligence`),enabled:Boolean(positionId),retry:false});
  const chart=useQuery({queryKey:["position-chart",positionId,"1Y"],queryFn:()=>getJson<PositionChartData>(`/api/v1/positions/${positionId}/chart?range=1Y`),enabled:Boolean(positionId),retry:false});
  if(report.isPending)return <main className="shell workspace-shell"><WorkspaceNav/><section className="context-card">正在加载持仓分析…</section></main>;
  if(report.isError)return <main className="shell workspace-shell"><WorkspaceNav/><section className="context-card" role="alert"><h1>持仓分析尚不可用</h1><p>系统不会用示例结论填充报告。</p><a href="/portfolio">返回我的持仓</a></section></main>;
  const data=report.data;const r=data.recommendation;const asset=data.assetEvidence;const company=asset?.company;const valuation=intelligence.data?.valuation;const stop=intelligence.data?.stop;const position=data.position;
  return <main className="shell workspace-shell position-detail-shell"><WorkspaceNav/><a className="back-link" href="/portfolio">← 返回我的持仓</a>
    <section className="position-hero"><div><p className="eyebrow">POSITION REPORT</p><h1>{position?.symbol??"—"}</h1><span>{position?.classification??"分类待确认"}</span></div><div className="position-verdict"><h2>{labels[r?.action??""]??r?.action??"等待数据"} <small>{r?.action}</small></h2><p className="analyst-line">{r?.resolutionReason??r?.reasons?.[0]??"分析证据尚未形成完整结论。"}</p><p>置信度 {r?.confidence??"WAIT_FOR_DATA"} · 当前 {pct(r?.currentWeight)} · 目标 {pct(r?.targetWeightMin)}–{pct(r?.targetWeightMax)}</p><p>建议数量 {quantity(data)} · 常规上限 {pct(r?.targetWeightMax)} · 硬上限 {pct(asset?.speculative?.hardMaxWeight)}</p></div></section>
    <section className="evidence-tiles"><Tile label="公司质量" value={company?.growthProfitabilityCashFlowStatus??(asset?.etf?"ETF MODEL":"MISSING")}/><Tile label="估值" value={company?.valuationStatus??valuation?.action}/><Tile label="预期修正" value={valuation?.earningsRevisions}/><Tile label="趋势" value={asset?.etf?.trendStatus??valuation?.priceStabilization}/><Tile label="组合容量" value={valuation?.portfolioCapacity===true?"AVAILABLE":valuation?.portfolioCapacity===false?"CONSTRAINED":"MISSING"} detail={`当前 ${pct(asset?.portfolioContext?.currentWeight)}`}/></section>
    <div className="position-modules">
      <section className="context-card"><span className="module-number">01</span><h2>财务质量</h2><dl className="financial-grid"><div><dt>Revenue</dt><dd>未由报告 API 返回</dd></div><div><dt>EPS</dt><dd>未由报告 API 返回</dd></div><div><dt>Operating margin</dt><dd>{company?.growthProfitabilityCashFlowStatus??"MISSING"}</dd></div><div><dt>Free cash flow</dt><dd>{company?.growthProfitabilityCashFlowStatus??"MISSING"}</dd></div><div><dt>Diluted shares</dt><dd>未由报告 API 返回</dd></div><div><dt>Net cash / debt</dt><dd>{company?.fundamentalsStatus??"MISSING"}</dd></div></dl></section>
      <section className="context-card"><span className="module-number">02</span><h2>估值</h2><dl className="financial-grid"><div><dt>Forward P/E</dt><dd>未由报告 API 返回</dd></div><div><dt>历史分位</dt><dd>{company?.valuationStatus??"MISSING"}</dd></div><div><dt>FCF yield</dt><dd>未由报告 API 返回</dd></div><div><dt>估值状态</dt><dd>{valuation?.valuationDiscount===true?"DISCOUNT":valuation?.valuationDiscount===false?"NO_DISCOUNT":"MISSING"}</dd></div></dl></section>
      <section className="context-card"><span className="module-number">03</span><h2>分析师预期</h2><dl className="financial-grid"><div><dt>FY1 EPS</dt><dd>未由报告 API 返回</dd></div><div><dt>30d / 90d 修正</dt><dd>{valuation?.earningsRevisions??"MISSING"}</dd></div><div><dt>分析师数量</dt><dd>未由报告 API 返回</dd></div><div><dt>分歧度</dt><dd>未由报告 API 返回</dd></div></dl></section>
      <section className="context-card"><span className="module-number">04</span><h2>风险与改变条件</h2>{r?.risks?.length?<ul>{r.risks.map(x=><li key={x}>{x}</li>)}</ul>:<p>暂无完整风险证据。</p>}<h3>改变建议的条件</h3><p>{r?.changeConditions?.length?r.changeConditions.join("；"):"暂无明确条件"}</p><p>正式止损 {stop?.liveStop??stop?.initialStop??"—"} · 软提醒 {stop?.softAlert??"—"}</p></section>
      <section className="context-card chart-module"><span className="module-number">05</span><h2>价格、均线与事件</h2>{chart.isPending?<p>正在加载真实日线…</p>:chart.isError?<div className="chart-empty">无法读取图表数据。</div>:<PositionChart data={chart.data}/>}<details><summary>高级指标</summary><p>SMA20、RSI 与 MACD 仅在相应真实序列由 API 返回后启用；当前不会从浏览器端补造。</p></details></section>
      <section className="context-card"><span className="module-number">06</span><h2>审计与时效</h2><dl className="module-metrics"><div><dt>分析状态</dt><dd>{data.evidence?.analysisStatus??"—"}</dd></div><div><dt>就绪状态</dt><dd>{data.readiness??"—"}</dd></div><div><dt>策略版本</dt><dd>{data.evidence?.strategyVersion??"—"}</dd></div><div><dt>数据截至</dt><dd>{data.dataAsOf?new Date(data.dataAsOf).toLocaleString("zh-CN"):"—"}</dd></div></dl><p>规则：{data.evidence?.ruleIds?.join(", ")||"—"}</p></section>
      <section className="context-card"><span className="module-number">07</span><h2>Thesis</h2><p>{company?.thesisStatus??"当前报告没有结构化 thesis 状态。"}</p></section>
      <section className="context-card"><span className="module-number">08</span><h2>财报 / 事件</h2><p>{company?.earningsRiskStatus??asset?.etf?.eventStatus??"当前没有可验证的事件证据。"}</p></section>
      <section className="context-card"><span className="module-number">09</span><h2>Cluster overlap</h2><p>集群权重 {pct(asset?.portfolioContext?.clusterWeight)} · 开放风险 {pct(asset?.portfolioContext?.clusterOpenRisk)}</p></section>
      <section className="context-card"><span className="module-number">10</span><h2>Tax lots</h2><p>当前报告 API 没有返回可核验税务批次；系统不会估算成本批次。</p></section>
      <section className="context-card"><span className="module-number">11</span><h2>决策历史</h2><p>行动确认与 recommendation audit 按持仓保存；当前页不把页面访问记为决策。</p></section>
      <section className="context-card"><span className="module-number">12</span><h2>数据来源和时效</h2><p>数据截至 {data.dataAsOf?new Date(data.dataAsOf).toLocaleString("zh-CN"):"待确认"}；配置哈希 {data.evidence?.configHash??"—"}。</p></section>
    </div><footer><span>仅供决策支持</span><span>证据缺失时不显示精确数量</span><span>不会自动交易</span></footer></main>;
}
