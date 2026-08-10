import { api, type components } from "@portfolio/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Database, Settings, ShieldCheck, SlidersHorizontal, Upload, UserRound } from "lucide-react";
import { ExecutiveDashboardPage } from "./dashboard/ExecutiveDashboardPage";
import { WorkspaceNav } from "./workspace-nav";

async function requireData<T>(request: Promise<{ data?: T; response: Response }>, label: string) { const {data,response}=await request; if (!data) throw new Error(`${label} unavailable (${String(response.status)})`); return data; }
const getSession = () => requireData(api.GET("/api/v1/auth/session"), "Session");
const getWorkerHealth = () => requireData(api.GET("/api/v1/worker/health"), "Worker health");
const getDataHealth = () => requireData(api.GET("/api/v1/market/data-health"), "Data health");
type CsrfToken = { headerName:string; parameterName:string; token:string };
async function submitSessionForm(path:"/api/v1/auth/login"|"/api/v1/auth/logout", fields:Record<string,string>={}) {
  const csrfResponse=await fetch("/api/v1/auth/csrf",{cache:"no-store",credentials:"same-origin"});
  if(!csrfResponse.ok) throw new Error("无法创建安全会话。");
  const csrf=await csrfResponse.json() as CsrfToken; const body=new URLSearchParams(fields); body.set(csrf.parameterName,csrf.token);
  const response=await fetch(path,{method:"POST",credentials:"same-origin",headers:{"Content-Type":"application/x-www-form-urlencoded",[csrf.headerName]:csrf.token},body});
  if(response.status===401) throw new Error("邮箱或密码不正确。"); if(response.status===429) throw new Error("登录尝试过多，请稍后重试。"); if(!response.ok) throw new Error(`会话请求失败 (${String(response.status)})。`);
}
function Frame({eyebrow,title,children}:{eyebrow:string;title:string;children:React.ReactNode}) { return <main className="shell workspace-shell"><WorkspaceNav/><section className="workspace-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1></div></section>{children}<footer><span>仅供决策支持</span><span>确认不等于执行</span><span>不会自动交易</span></footer></main>; }
export function DashboardPage(){return <ExecutiveDashboardPage/>;}

type Brief = components["schemas"]["ExecutiveBrief"];
function OpportunityList({title,items}:{title:string;items:components["schemas"]["BriefAction"][]}) { return <article className="context-card"><h2>{title}</h2>{items.length?<ul>{items.map(x=><li key={x.id}><a href={x.positionId?`/positions/${x.positionId}`:"/portfolio"}><strong>{x.symbol??"组合"}</strong> · {x.action} · {x.confidence}</a></li>)}</ul>:<p>当前没有通过数据完整性与风险约束的候选。</p>}</article>; }
export function OpportunitiesPage(){
  const brief=useQuery({queryKey:["opportunity-brief"],queryFn:()=>requireData<Brief>(api.GET("/api/v1/brief/today"),"Brief"),retry:false});
  const actions=brief.data?[...brief.data.mustAct,...brief.data.watch]:[];
  const quality=actions.filter(x=>/QUALITY|CORE|STOCK/.test(x.classification??"")); const etf=actions.filter(x=>/ETF/.test(x.classification??""));
  return <Frame eyebrow="MARKET & OPPORTUNITIES" title="市场与机会">{brief.isPending?<section className="context-card">正在读取真实候选…</section>:brief.isError?<section className="context-card" role="alert">无法读取机会数据。</section>:<section className="opportunity-grid"><OpportunityList title="Quality Discount" items={quality}/><OpportunityList title="ETF Dip" items={etf}/><OpportunityList title="Watchlist" items={brief.data.watch}/></section>}<a href="/advanced/market-context">查看市场状态与数据依据 →</a></Frame>;
}

type History = components["schemas"]["HistoryResponse"];
export function ReviewPage(){
  const history=useQuery({queryKey:["recommendation-history"],queryFn:()=>requireData<History[]>(api.GET("/api/v1/recommendations/history"),"History"),retry:false});
  const rows=history.data??[]; const completed=rows.filter(x=>!["ACTIVE",undefined].includes(x.status));
  return <Frame eyebrow="DECISION REVIEW" title="复盘"><section className="review-grid"><article className="context-card"><p className="eyebrow">WEEKLY</p><h2>本周行动结果</h2><strong className="large-status">{completed.length} / {rows.length}</strong><p>已关闭建议 / 全部建议记录</p><ul>{rows.slice(0,5).map((x,i)=><li key={`${x.symbol??"portfolio"}-${x.dataAsOf??String(i)}`}>{x.symbol??"组合"} · {x.action??"—"} · {x.status??"—"}</li>)}</ul></article><article className="context-card"><p className="eyebrow">WEEKLY</p><h2>规则违反与最大错误</h2><p>规则 ID 与决策状态来自审计记录；没有可验证记录时不推断错误。</p><strong className="large-status">{rows.filter(x=>x.status==="IGNORED").length}</strong><p>被忽略的建议</p></article><article className="context-card"><p className="eyebrow">MONTHLY</p><h2>组合绩效</h2><p>组合收益、QQQ / SPY、主动仓位、最大回撤和换手率将在可验证绩效序列生成后显示。</p></article><article className="context-card"><p className="eyebrow">MONTHLY</p><h2>交易质量</h2><p>平均 R、MFE / MAE 与水下曲线仅从真实 journal 和净值数据计算，不使用占位数值。</p><a href="/portfolio">打开持仓日志 →</a></article></section>{history.isError?<p role="alert">无法读取复盘历史。</p>:null}</Frame>;
}
export function PlanPage(){return <Frame eyebrow="PRE-COMMITMENT CHECK" title="Trade plan"><section className="context-card"><h2>执行前检查</h2><p>分类、风险上限、冷静期、证据质量与 thesis 状态会在显示数量前完成检查。</p><a href="/portfolio">打开组合计划 →</a></section></Frame>;}
export function ThesisPage(){return <Frame eyebrow="CHANGE CONDITIONS" title="Thesis"><section className="context-card"><h2>持仓级证据</h2><p>确认、估值修正、财报风险和有效期都保存在持仓报告中。</p><a href="/portfolio">选择持仓 →</a></section></Frame>;}
export function JournalPage(){return <Frame eyebrow="AUDITABLE DECISIONS" title="Journal"><section className="context-card"><h2>风险优先</h2><p>日志记录证据、风险、规则 ID 和改变决策的条件。</p><a href="/portfolio">打开持仓日志 →</a></section></Frame>;}

export function SettingsPage(){
  const queryClient=useQueryClient(); const session=useQuery({queryKey:["session"],queryFn:getSession});
  const csrf=useQuery({queryKey:["csrf"],queryFn:()=>requireData(api.GET("/api/v1/auth/csrf"),"CSRF")});
  const dataHealth=useQuery({queryKey:["settings-data-health"],queryFn:getDataHealth,retry:false}); const worker=useQuery({queryKey:["settings-worker-health"],queryFn:getWorkerHealth,retry:false});
  const sessionAction=useMutation({mutationFn:async(action:{type:"login";username:string;password:string}|{type:"logout"})=>{if(action.type==="login")await submitSessionForm("/api/v1/auth/login",{username:action.username,password:action.password});else await submitSessionForm("/api/v1/auth/logout");},onSuccess:async()=>{await Promise.all([queryClient.invalidateQueries({queryKey:["session"]}),queryClient.invalidateQueries({queryKey:["executive-brief-today"]}),csrf.refetch()]);}});
  const token=csrf.data;
  return <Frame eyebrow="ACCOUNT, POLICY & PROVIDERS" title="设置"><section className="settings-grid">
    {/* eslint-disable-next-line @typescript-eslint/restrict-template-expressions -- username is optional in the generated session contract. */}
    <article className="context-card"><UserRound/><h2>{session.data?.authenticated?"Authenticated session":"Account"}</h2><p>{session.data?.authenticated?`已登录：${session.data.username}`:"使用同源 HttpOnly 会话登录。No access token is stored in browser storage."}</p>{session.data?.authenticated?<button onClick={()=>{sessionAction.mutate({type:"logout"});}}>退出登录</button>:<form className="login-form" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);const username=f.get("username");const password=f.get("password");sessionAction.mutate({type:"login",username:typeof username==="string"?username:"",password:typeof password==="string"?password:""});}}><label>Email<input required autoComplete="username" name="username" type="email"/></label><label>Password<input required autoComplete="current-password" name="password" type="password"/></label>{token?<input type="hidden" name={token.parameterName} value={token.token}/>:null}<button type="submit" disabled={!token||sessionAction.isPending}>Sign in</button></form>}{sessionAction.isError?<p role="alert">{sessionAction.error.message}</p>:null}</article>
    <article className="context-card"><Upload/><h2>Import</h2><p>导入 Fidelity CSV、粘贴表格或手工持仓，并在写入前预览。</p><a href="/portfolio/import">管理导入 →</a></article>
    <article className="context-card"><SlidersHorizontal/><h2>Strategy</h2><p>策略版本由服务端配置和审计哈希控制。</p><a href="/advanced/strategy">查看策略配置 →</a></article>
    <article className="context-card"><Database/><h2>Data Providers</h2><dl className="module-metrics"><div><dt>能力 / 状态</dt><dd>{dataHealth.isPending?"CHECKING":dataHealth.isError?"UNAVAILABLE":dataHealth.data.status}</dd></div><div><dt>任务队列</dt><dd>{worker.data?`${String(worker.data.pendingJobs)} pending / ${String(worker.data.deadJobs)} dead`:"受保护"}</dd></div></dl><p>最新成功时间、配额和错误由 provider health API 报告；当前接口未返回的字段不做推测。</p><a href="/advanced/data-health">查看数据健康 →</a></article>
    <article className="context-card"><ShieldCheck/><h2>Cash Policy</h2><p>应急现金与战术储备在仓位计算前扣除。</p></article>
    <article className="context-card"><Settings/><h2>Advanced</h2><p>审计、运行状态和高级诊断集中在受保护页面。</p><a href="/advanced/data-health">打开高级诊断 →</a></article>
  </section></Frame>;
}
export function DataHealthPage(){const data=useQuery({queryKey:["data-health"],queryFn:getDataHealth});const worker=useQuery({queryKey:["worker-health"],queryFn:getWorkerHealth});return <Frame eyebrow="OBSERVABILITY" title="Data health"><section className="dashboard-grid"><article className="context-card"><h2>Market data</h2><strong className="large-status">{data.isPending?"CHECKING":data.isError?"UNAVAILABLE":data.data.status}</strong><p>过期或缺失证据会抑制精确建议。</p></article><article className="context-card"><h2>Durable worker</h2><strong className="large-status">{worker.isPending?"CHECKING":worker.isError?"PROTECTED":`${String(worker.data.pendingJobs)} PENDING`}</strong><p>{worker.data?`${String(worker.data.deadJobs)} dead jobs require review.`:"请登录查看私有任务队列。"}</p></article></section></Frame>;}
